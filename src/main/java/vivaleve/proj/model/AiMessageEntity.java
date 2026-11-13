package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vivaleve.proj.model.enums.AiInteractionStatus;
import vivaleve.proj.model.enums.AiMessageRole;

/**
 * Cada interação com a IA (PL-25): a mensagem do usuário e a resposta do modelo,
 * com metadados para auditoria e custo (modelo, tokens, latência, status).
 */
@Table(name = "ia_mensagens", indexes = @Index(name = "idx_ia_mensagens_conversa", columnList = "conversa_id"))
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AiMessageEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private AiConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 15)
    private AiMessageRole role;

    @Column(name = "conteudo", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AiInteractionStatus status;

    /** Modelo que gerou a resposta (nulo em mensagens do usuário e respostas fixas). */
    @Column(name = "modelo", length = 80)
    private String model;

    @Column(name = "tokens_prompt")
    private Integer promptTokens;

    @Column(name = "tokens_resposta")
    private Integer responseTokens;

    @Column(name = "latencia_ms")
    private Long latencyMs;

    @Column(name = "risco_detectado", nullable = false)
    private boolean crisisDetected;
}
