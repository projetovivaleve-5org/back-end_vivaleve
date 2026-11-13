package vivaleve.proj.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vivaleve.proj.dto.AiDtos.AiResponse;
import vivaleve.proj.dto.AiDtos.ChatRequest;
import vivaleve.proj.dto.AiDtos.MessageResponse;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.model.enums.AiInteractionStatus;
import vivaleve.proj.model.enums.AiMessageRole;
import vivaleve.proj.service.ai.AiClient;
import vivaleve.proj.service.ai.AiClient.AiCompletion;
import vivaleve.proj.service.ai.AiService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Armazenamento das interações (PL-25) de ponta a ponta com banco H2:
 * chat de 2 turnos -> mensagens gravadas com metadados -> listagem -> exclusão em cascata.
 * Só o cliente do Groq é simulado.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AiPersistenceIntegrationTest {

    @Autowired AiService aiService;
    @Autowired UserRepository userRepository;
    @Autowired AiConversationRepository conversationRepository;
    @Autowired AiMessageRepository messageRepository;

    @MockitoBean AiClient aiClient;

    @Test
    void gravaConversaComMetadadosEApagaEmCascata() {
        UserEntity user = userRepository.save(
                UserEntity.builder().email("persist@pluma.com").password("123456").build());
        when(aiClient.complete(anyList()))
                .thenReturn(new AiCompletion("Oi! Como você está?", "openai/gpt-oss-20b", 120, 9, 850))
                .thenReturn(new AiCompletion("Entendo, vamos respirar juntos?", "openai/gpt-oss-20b", 180, 12, 700));

        AiResponse first = aiService.chat(user.getId(), new ChatRequest(null, "oi", "Ana"));
        AiResponse second = aiService.chat(user.getId(), new ChatRequest(first.conversationId(), "estou ansiosa", "Ana"));

        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(first.messageId()).isNotNull();
        assertThat(second.createdAt()).isNotNull();

        List<MessageResponse> messages = aiService.listMessages(first.conversationId(), user.getId());
        assertThat(messages).extracting(MessageResponse::role).containsExactly(
                AiMessageRole.USER, AiMessageRole.ASSISTANT, AiMessageRole.USER, AiMessageRole.ASSISTANT);
        assertThat(messages).extracting(MessageResponse::content).containsExactly(
                "oi", "Oi! Como você está?", "estou ansiosa", "Entendo, vamos respirar juntos?");
        assertThat(messages).extracting(MessageResponse::status).containsExactly(
                AiInteractionStatus.RECEIVED, AiInteractionStatus.SUCCESS,
                AiInteractionStatus.RECEIVED, AiInteractionStatus.SUCCESS);

        var stored = messageRepository.findById(second.messageId()).orElseThrow();
        assertThat(stored.getModel()).isEqualTo("openai/gpt-oss-20b");
        assertThat(stored.getPromptTokens()).isEqualTo(180);
        assertThat(stored.getResponseTokens()).isEqualTo(12);
        assertThat(stored.getLatencyMs()).isEqualTo(700);

        assertThat(aiService.listConversations(user.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.title()).isEqualTo("oi"));

        aiService.deleteConversation(first.conversationId(), user.getId());

        assertThat(conversationRepository.findById(first.conversationId())).isEmpty();
        assertThat(messageRepository.findByConversation_IdOrderByIdAsc(first.conversationId())).isEmpty();
    }

    @Test
    void textoLongoCabeNaColunaDeConteudo() {
        UserEntity user = userRepository.save(
                UserEntity.builder().email("longo@pluma.com").password("123456").build());
        when(aiClient.complete(anyList())).thenReturn(new AiCompletion("ok", "m", 1, 1, 1));

        AiResponse resp = aiService.chat(user.getId(), new ChatRequest(null, "x".repeat(2000), null));

        assertThat(aiService.listMessages(resp.conversationId(), user.getId()).getFirst().content()).hasSize(2000);
    }
}
