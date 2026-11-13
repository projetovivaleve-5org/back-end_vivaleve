package vivaleve.proj.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.config.AiProperties;
import vivaleve.proj.dto.AiDtos.*;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.model.AiConversationEntity;
import vivaleve.proj.model.AiMessageEntity;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.model.enums.*;
import vivaleve.proj.repository.AiConversationRepository;
import vivaleve.proj.repository.AiMessageRepository;
import vivaleve.proj.repository.EntryRepository;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.StatsService;
import vivaleve.proj.service.ai.AiClient.AiChatMessage;
import vivaleve.proj.service.ai.AiClient.AiCompletion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regras do AiService com o modelo e o banco simulados. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    @Mock AiClient aiClient;
    @Mock AiConversationRepository conversationRepository;
    @Mock AiMessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock EntryRepository entryRepository;
    @Mock StatsService statsService;

    private AiService service;
    private PromptFactory promptFactory;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties(null, "key", null, null, null, null, null, 10, null);
        promptFactory = new PromptFactory(props);
        service = new AiService(aiClient, promptFactory, new CrisisDetector(), conversationRepository,
                messageRepository, userRepository, entryRepository, statsService, props);

        user = UserEntity.builder().email("ana@pluma.com").password("123456").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void chatNovoChamaModeloPadronizaESalvaAsDuasMensagens() {
        when(aiClient.complete(anyList()))
                .thenReturn(new AiCompletion("  **Oi, Ana!** Estou aqui.  ", "openai/gpt-oss-20b", 50, 8, 900));

        AiResponse resp = service.chat(1L, new ChatRequest(null, "oi, tudo bem?", "Ana"));

        assertThat(resp.status()).isEqualTo(AiInteractionStatus.SUCCESS);
        assertThat(resp.reply()).isEqualTo("Oi, Ana! Estou aqui.");
        assertThat(resp.useCase()).isEqualTo(AiUseCase.CHAT_APOIO);
        assertThat(resp.model()).isEqualTo("openai/gpt-oss-20b");
        assertThat(resp.crisisDetected()).isFalse();
        assertThat(resp.helpResources()).isEmpty();

        ArgumentCaptor<AiMessageEntity> saved = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageRepository, times(2)).save(saved.capture());
        AiMessageEntity userMsg = saved.getAllValues().get(0);
        AiMessageEntity botMsg = saved.getAllValues().get(1);
        assertThat(userMsg.getRole()).isEqualTo(AiMessageRole.USER);
        assertThat(userMsg.getStatus()).isEqualTo(AiInteractionStatus.RECEIVED);
        assertThat(botMsg.getRole()).isEqualTo(AiMessageRole.ASSISTANT);
        assertThat(botMsg.getPromptTokens()).isEqualTo(50);
        assertThat(botMsg.getResponseTokens()).isEqualTo(8);
        assertThat(botMsg.getLatencyMs()).isEqualTo(900);

        ArgumentCaptor<AiConversationEntity> conv = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository, atLeastOnce()).save(conv.capture());
        assertThat(conv.getValue().getTitle()).isEqualTo("oi, tudo bem?");
        assertThat(conv.getValue().getUser()).isSameAs(user);
    }

    @Test
    void mensagemDeRiscoNaoChamaModeloEDevolveCvv() {
        AiResponse resp = service.chat(1L, new ChatRequest(null, "não quero mais viver", null));

        verifyNoInteractions(aiClient);
        assertThat(resp.status()).isEqualTo(AiInteractionStatus.CRISIS);
        assertThat(resp.crisisDetected()).isTrue();
        assertThat(resp.reply()).contains("188");
        assertThat(resp.helpResources()).anyMatch(r -> r.contains("188"));

        ArgumentCaptor<AiMessageEntity> saved = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allMatch(AiMessageEntity::isCrisisDetected);
    }

    @Test
    void falhaDoModeloDevolveFallbackSemErroParaOUsuario() {
        when(aiClient.complete(anyList())).thenThrow(new AiClientException("timeout"));

        AiResponse resp = service.chat(1L, new ChatRequest(null, "estou ansioso", null));

        assertThat(resp.status()).isEqualTo(AiInteractionStatus.FALLBACK);
        assertThat(resp.reply()).isEqualTo(promptFactory.fallbackReply());
    }

    @Test
    void respostaVaziaDepoisDePadronizarViraFallback() {
        when(aiClient.complete(anyList()))
                .thenReturn(new AiCompletion("<think>hmm</think>", "openai/gpt-oss-20b", 1, 1, 1));

        AiResponse resp = service.chat(1L, new ChatRequest(null, "oi", null));

        assertThat(resp.status()).isEqualTo(AiInteractionStatus.FALLBACK);
    }

    @Test
    void conversaExistenteEnviaHistoricoEmOrdemCronologicaSemFallbacks() {
        AiConversationEntity conv = conversation(5L, AiUseCase.CHAT_APOIO);
        when(conversationRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(conv));
        // o repositório devolve do mais novo para o mais antigo
        when(messageRepository.findByConversation_IdOrderByIdDesc(eq(5L), any(Pageable.class))).thenReturn(List.of(
                message(AiMessageRole.ASSISTANT, "fallback antigo", AiInteractionStatus.FALLBACK),
                message(AiMessageRole.ASSISTANT, "Como você está?", AiInteractionStatus.SUCCESS),
                message(AiMessageRole.USER, "oi", AiInteractionStatus.RECEIVED)
        ));
        when(aiClient.complete(anyList())).thenReturn(new AiCompletion("Entendo.", "m", 1, 1, 1));

        service.chat(1L, new ChatRequest(5L, "estou cansado", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(aiClient).complete(prompt.capture());
        assertThat(prompt.getValue()).extracting(AiChatMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(prompt.getValue()).extracting(AiChatMessage::content)
                .doesNotContain("fallback antigo")
                .endsWith("estou cansado");
    }

    @Test
    void conversaDeOutroUsuarioDa404() {
        when(conversationRepository.findByIdAndUser_Id(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat(1L, new ChatRequest(9L, "oi", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Conversa não encontrada");
        verifyNoInteractions(aiClient);
    }

    @Test
    void conversaDeOutroCasoDeUsoNaoAceitaChat() {
        when(conversationRepository.findByIdAndUser_Id(5L, 1L))
                .thenReturn(Optional.of(conversation(5L, AiUseCase.INSIGHT_HUMOR)));

        assertThatThrownBy(() -> service.chat(1L, new ChatRequest(5L, "oi", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chat de apoio");
    }

    @Test
    void usuarioInexistenteDa404() {
        assertThatThrownBy(() -> service.chat(99L, new ChatRequest(null, "oi", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Usuário não encontrado");
    }

    @Test
    void insightDeHumorUsaAsEstatisticasDoPeriodo() {
        StatsSummary stats = new StatsSummary(30, LocalDate.now().minusDays(29), LocalDate.now(),
                0, null, 0, 0, List.of(), List.of());
        when(statsService.gerarResumo(1L, 30)).thenReturn(stats);
        when(aiClient.complete(anyList())).thenReturn(new AiCompletion("Que tal começar hoje?", "m", 1, 1, 1));

        AiResponse resp = service.moodInsight(1L, new MoodInsightRequest(30, null));

        assertThat(resp.useCase()).isEqualTo(AiUseCase.INSIGHT_HUMOR);
        assertThat(resp.status()).isEqualTo(AiInteractionStatus.SUCCESS);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(aiClient).complete(prompt.capture());
        assertThat(prompt.getValue().getLast().content()).contains("últimos 30 dias", "sem registros");
    }

    @Test
    void reflexaoDeRegistroDeOutroUsuarioDa404() {
        UserEntity outro = UserEntity.builder().email("x@x.com").password("123456").build();
        ReflectionTestUtils.setField(outro, "id", 2L);
        EntryEntity entry = new EntryEntity();
        entry.setUser(outro);
        when(entryRepository.findById(7L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.entryReflection(1L, 7L, new EntryReflectionRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Registro não encontrado");
        verifyNoInteractions(aiClient);
    }

    @Test
    void reflexaoDeRegistroComNotaDeRiscoDevolveCrise() {
        EntryEntity entry = new EntryEntity();
        entry.setUser(user);
        entry.setMood(Mood.AWFUL);
        entry.setDate(LocalDate.now());
        entry.setNote("hoje pensei em me machucar");
        when(entryRepository.findById(7L)).thenReturn(Optional.of(entry));

        AiResponse resp = service.entryReflection(1L, 7L, new EntryReflectionRequest(null));

        assertThat(resp.status()).isEqualTo(AiInteractionStatus.CRISIS);
        verifyNoInteractions(aiClient);
    }

    private AiConversationEntity conversation(Long id, AiUseCase useCase) {
        AiConversationEntity c = new AiConversationEntity();
        ReflectionTestUtils.setField(c, "id", id);
        c.setUser(user);
        c.setUseCase(useCase);
        return c;
    }

    private static AiMessageEntity message(AiMessageRole role, String content, AiInteractionStatus status) {
        AiMessageEntity m = new AiMessageEntity();
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        return m;
    }
}
