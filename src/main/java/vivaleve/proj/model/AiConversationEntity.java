package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vivaleve.proj.model.enums.AiUseCase;

import java.time.LocalDateTime;

/** Uma conversa com a IA (PL-25). Agrupa as mensagens de um mesmo caso de uso. */
@Table(name = "ia_conversas")
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AiConversationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "caso_uso", nullable = false, length = 30)
    private AiUseCase useCase;

    @Column(name = "titulo", length = 120)
    private String title;

    /** Atualizado a cada mensagem, para ordenar a lista de conversas. */
    @Column(name = "ultima_mensagem_em")
    private LocalDateTime lastMessageAt;

    // Sem @OneToMany de mensagens de propósito: a conversa é salva (merge) a cada turno e uma
    // coleção desatualizada com orphanRemoval apagaria mensagens. A exclusão é feita no AiService.
}
