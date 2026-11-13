package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Sessão concluída do exercício de respiração (tela respirar.html).
 *
 * <p>{@code sessionDate} é o dia da sessão no fuso do Brasil (America/Sao_Paulo), igual ao
 * MoodLog. Assim "sessões de hoje" não vira o dia às 21h no servidor do Render (que roda em UTC).
 * A hora exata fica no {@code createdAt} da {@link BaseEntity}.</p>
 */
@Table(name = "respiracao_sessoes")
@Getter
@Setter
@NoArgsConstructor
@Entity
public class BreathingSessionEntity extends BaseEntity {

    /** Técnica usada, ex.: "3-4-5" (inspira 3s, segura 4s, expira 5s). */
    @Column(name = "tecnica", nullable = false, length = 30)
    private String technique;

    @Column(name = "ciclos", nullable = false)
    private Integer cycles;

    @Column(name = "duracao_segundos", nullable = false)
    private Integer durationSeconds;

    @Column(name = "data_sessao", nullable = false)
    private LocalDate sessionDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity user;
}
