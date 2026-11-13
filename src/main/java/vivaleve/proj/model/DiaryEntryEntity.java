package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vivaleve.proj.model.enums.MoodType;

/**
 * Entrada do diário emocional (tela diario.html do front).
 *
 * <p>Cada entrada tem um título, o texto livre e, opcionalmente, a emoção do momento
 * (os mesmos valores da tela: OTIMO, BEM, MAIS_OU_MENOS, MAL, DEPRESSIVO, ANSIOSO).
 * A data/hora vem do {@code createdAt} da {@link BaseEntity}.</p>
 *
 * <p>LGPD: o texto do diário é dado sensível de saúde. Nunca registrar o conteúdo em log.</p>
 */
@Table(name = "diario_entradas")
@Getter
@Setter
@NoArgsConstructor
@Entity
public class DiaryEntryEntity extends BaseEntity {

    @Column(name = "titulo", nullable = false, length = 100)
    private String title;

    @Column(name = "texto", nullable = false, columnDefinition = "TEXT")
    private String text;

    /** Emoção escolhida na entrada (opcional). */
    @Enumerated(EnumType.STRING)
    @Column(name = "emocao", length = 20)
    private MoodType emotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity user;
}
