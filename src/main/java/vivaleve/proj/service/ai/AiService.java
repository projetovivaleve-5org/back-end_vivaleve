package vivaleve.proj.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.config.AiProperties;
import vivaleve.proj.dto.AiDtos.*;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.model.AiConversationEntity;
import vivaleve.proj.model.AiMessageEntity;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.model.enums.AiInteractionStatus;
import vivaleve.proj.model.enums.AiMessageRole;
import vivaleve.proj.model.enums.AiUseCase;
import vivaleve.proj.repository.AiConversationRepository;
import vivaleve.proj.repository.AiMessageRepository;
import vivaleve.proj.repository.EntryRepository;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.StatsService;
import vivaleve.proj.service.ai.AiClient.AiChatMessage;
import vivaleve.proj.service.ai.AiClient.AiCompletion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Orquestra os casos de uso de IA: valida, checa risco, monta prompt, chama o modelo,
 * padroniza a resposta e guarda a interação.
 *
 * Os métodos que chamam o modelo NÃO são @Transactional de propósito: a chamada HTTP pode
 * levar vários segundos e não deve segurar uma conexão do banco aberta esse tempo todo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    static final List<String> HELP_RESOURCES = List.of(
            "CVV - Centro de Valorização da Vida: ligue 188 (24h, gratuito) ou acesse cvv.org.br",
            "SAMU: 192",
            "Em perigo imediato, vá ao pronto-socorro mais próximo"
    );

    private final AiClient aiClient;
    private final PromptFactory promptFactory;
    private final CrisisDetector crisisDetector;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EntryRepository entryRepository;
    private final StatsService statsService;
    private final AiProperties props;

    // ------------------------------------------------------------------ casos de uso

    /** CHAT_APOIO: conversa multi-turno da tela de chat. */
    public AiResponse chat(Long userId, ChatRequest request) {
        UserEntity user = findUser(userId);
        String message = request.message().strip();

        AiConversationEntity conversation;
        List<AiMessageEntity> history;
        if (request.conversationId() == null) {
            conversation = newConversation(user, AiUseCase.CHAT_APOIO, promptFactory.titleFrom(message));
            history = List.of();
        } else {
            conversation = findConversation(request.conversationId(), userId);
            if (conversation.getUseCase() != AiUseCase.CHAT_APOIO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta conversa não é do chat de apoio");
            }
            history = recentHistory(conversation.getId());
        }

        boolean crisis = crisisDetector.isCrisis(message);
        List<AiChatMessage> prompt = crisis ? null : promptFactory.buildChat(request.userName(), history, message);
        return run(conversation, message, crisis, prompt);
    }

    /** INSIGHT_HUMOR: leitura das estatísticas de humor do período. */
    public AiResponse moodInsight(Long userId, MoodInsightRequest request) {
        UserEntity user = findUser(userId);
        int days = request.days() == null ? 7 : request.days();
        StatsSummary stats = statsService.gerarResumo(userId, days);

        String context = promptFactory.describeStats(stats);
        AiConversationEntity conversation =
                newConversation(user, AiUseCase.INSIGHT_HUMOR, "Insight de humor - " + days + " dias");
        return run(conversation, context, false, promptFactory.buildMoodInsight(request.userName(), stats));
    }

    /** REFLEXAO_REGISTRO: comentário sobre um registro. A nota do registro também passa pela checagem de risco. */
    public AiResponse entryReflection(Long userId, Long entryId, EntryReflectionRequest request) {
        UserEntity user = findUser(userId);
        EntryEntity entry = entryRepository.findById(entryId)
                .filter(e -> Objects.equals(e.getUser().getId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));

        String context = promptFactory.describeEntry(entry);
        boolean crisis = crisisDetector.isCrisis(entry.getNote());
        AiConversationEntity conversation =
                newConversation(user, AiUseCase.REFLEXAO_REGISTRO, "Reflexão do registro de " + entry.getDate());
        List<AiChatMessage> prompt = crisis ? null : promptFactory.buildEntryReflection(request.userName(), entry);
        return run(conversation, context, crisis, prompt);
    }

    // ------------------------------------------------------------------ histórico (PL-25)

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(Long userId) {
        return conversationRepository.findByUser_IdOrderByLastMessageAtDesc(userId).stream()
                .map(ConversationSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long conversationId, Long userId) {
        findConversation(conversationId, userId);
        return messageRepository.findByConversation_IdOrderByIdAsc(conversationId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        AiConversationEntity conversation = findConversation(conversationId, userId);
        messageRepository.deleteAllByConversationId(conversationId);
        conversationRepository.delete(conversation);
    }

    // ------------------------------------------------------------------ fluxo comum

    /**
     * Guarda a mensagem do usuário, gera a resposta (crise / modelo / fallback), guarda a resposta
     * e devolve o envelope padrão.
     */
    private AiResponse run(AiConversationEntity conversation, String userContent,
                           boolean crisis, List<AiChatMessage> prompt) {
        saveMessage(conversation, AiMessageRole.USER, userContent, AiInteractionStatus.RECEIVED, crisis, null);

        AiMessageEntity reply;
        if (crisis) {
            log.info("Risco detectado na conversa {} — respondendo com mensagem de segurança", conversation.getId());
            reply = saveMessage(conversation, AiMessageRole.ASSISTANT, promptFactory.crisisReply(),
                    AiInteractionStatus.CRISIS, true, null);
        } else {
            reply = callModel(conversation, prompt);
        }

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return new AiResponse(
                conversation.getId(),
                reply.getId(),
                conversation.getUseCase(),
                reply.getStatus(),
                reply.getContent(),
                crisis,
                crisis ? HELP_RESOURCES : List.of(),
                reply.getModel(),
                reply.getCreatedAt() != null ? reply.getCreatedAt() : LocalDateTime.now()
        );
    }

    private AiMessageEntity callModel(AiConversationEntity conversation, List<AiChatMessage> prompt) {
        try {
            AiCompletion completion = aiClient.complete(prompt);
            String text = promptFactory.sanitizeReply(completion.content());
            if (text == null) {
                throw new AiClientException("Resposta vazia após padronização");
            }
            AiMessageEntity msg = newMessage(conversation, AiMessageRole.ASSISTANT, text, AiInteractionStatus.SUCCESS, false);
            msg.setModel(completion.model());
            msg.setPromptTokens(completion.promptTokens());
            msg.setResponseTokens(completion.responseTokens());
            msg.setLatencyMs(completion.latencyMs());
            return messageRepository.save(msg);
        } catch (AiClientException e) {
            log.warn("IA indisponível na conversa {}: {} — usando fallback", conversation.getId(), e.getMessage());
            return saveMessage(conversation, AiMessageRole.ASSISTANT, promptFactory.fallbackReply(),
                    AiInteractionStatus.FALLBACK, false, null);
        }
    }

    /** Últimas N mensagens em ordem cronológica, sem as respostas de fallback (não agregam contexto). */
    private List<AiMessageEntity> recentHistory(Long conversationId) {
        List<AiMessageEntity> recent = new ArrayList<>(messageRepository.findByConversation_IdOrderByIdDesc(
                conversationId, PageRequest.of(0, props.historyLimit())));
        recent.removeIf(m -> m.getStatus() == AiInteractionStatus.FALLBACK);
        Collections.reverse(recent);
        return recent;
    }

    private AiConversationEntity newConversation(UserEntity user, AiUseCase useCase, String title) {
        AiConversationEntity c = new AiConversationEntity();
        c.setUser(user);
        c.setUseCase(useCase);
        c.setTitle(title);
        c.setLastMessageAt(LocalDateTime.now());
        return conversationRepository.save(c);
    }

    private AiMessageEntity saveMessage(AiConversationEntity conversation, AiMessageRole role, String content,
                                        AiInteractionStatus status, boolean crisis, String model) {
        AiMessageEntity m = newMessage(conversation, role, content, status, crisis);
        m.setModel(model);
        return messageRepository.save(m);
    }

    private AiMessageEntity newMessage(AiConversationEntity conversation, AiMessageRole role, String content,
                                       AiInteractionStatus status, boolean crisis) {
        AiMessageEntity m = new AiMessageEntity();
        m.setConversation(conversation);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        m.setCrisisDetected(crisis);
        return m;
    }

    private UserEntity findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private AiConversationEntity findConversation(Long conversationId, Long userId) {
        return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"));
    }
}
