package vivaleve.proj.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vivaleve.proj.model.AiConversationEntity;
import vivaleve.proj.model.AiMessageEntity;
import vivaleve.proj.model.enums.AiInteractionStatus;
import vivaleve.proj.model.enums.AiMessageRole;
import vivaleve.proj.model.enums.AiUseCase;

import java.time.LocalDateTime;
import java.util.List;

/** Contratos JSON da camada de IA (PL-24). */
public final class AiDtos {

    private AiDtos() {
    }

    /** POST /v1/ai/chat — sem conversationId abre uma conversa nova. */
    public record ChatRequest(
            Long conversationId,
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 60) String userName
    ) {
    }

    /** POST /v1/ai/insights/mood — days aceita 7 ou 30 (padrão 7). */
    public record MoodInsightRequest(
            Integer days,
            @Size(max = 60) String userName
    ) {
    }

    /** POST /v1/ai/entries/{entryId}/reflection */
    public record EntryReflectionRequest(
            @Size(max = 60) String userName
    ) {
    }

    /**
     * Envelope padrão de TODA resposta de IA.
     * O front deve olhar "status": SUCCESS (resposta do modelo), FALLBACK (modelo indisponível,
     * texto padrão) ou CRISIS (risco detectado — mostrar "helpResources" em destaque).
     */
    public record AiResponse(
            Long conversationId,
            Long messageId,
            AiUseCase useCase,
            AiInteractionStatus status,
            String reply,
            boolean crisisDetected,
            List<String> helpResources,
            String model,
            LocalDateTime createdAt
    ) {
    }

    public record ConversationSummary(
            Long id,
            AiUseCase useCase,
            String title,
            LocalDateTime createdAt,
            LocalDateTime lastMessageAt
    ) {
        public static ConversationSummary from(AiConversationEntity c) {
            return new ConversationSummary(c.getId(), c.getUseCase(), c.getTitle(), c.getCreatedAt(), c.getLastMessageAt());
        }
    }

    public record MessageResponse(
            Long id,
            AiMessageRole role,
            String content,
            AiInteractionStatus status,
            LocalDateTime createdAt
    ) {
        public static MessageResponse from(AiMessageEntity m) {
            return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getStatus(), m.getCreatedAt());
        }
    }
}
