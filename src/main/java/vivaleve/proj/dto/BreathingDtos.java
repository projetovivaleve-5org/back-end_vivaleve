package vivaleve.proj.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vivaleve.proj.model.BreathingSessionEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Formato do JSON das rotas de respiração ({@code /v1/breathing}). */
public final class BreathingDtos {

    private BreathingDtos() {
    }

    /** Corpo de POST /v1/breathing/sessions (sessão concluída). Não tem userId: o dono vem do JWT. */
    public record BreathingSessionRequest(
            @NotBlank(message = "A técnica é obrigatória")
            @Size(max = 30, message = "A técnica deve ter no máximo 30 caracteres")
            String technique,

            @NotNull(message = "Informe os ciclos")
            @Min(value = 1, message = "A sessão precisa de pelo menos 1 ciclo")
            @Max(value = 100, message = "No máximo 100 ciclos por sessão")
            Integer cycles,

            @NotNull(message = "Informe a duração")
            @Min(value = 1, message = "A duração deve ser de pelo menos 1 segundo")
            @Max(value = 7200, message = "A duração deve ser de no máximo 2 horas")
            Integer durationSeconds
    ) {
    }

    public record BreathingSessionResponse(
            Long id,
            String technique,
            Integer cycles,
            Integer durationSeconds,
            LocalDate sessionDate,
            LocalDateTime createdAt
    ) {
        public static BreathingSessionResponse from(BreathingSessionEntity s) {
            return new BreathingSessionResponse(s.getId(), s.getTechnique(), s.getCycles(),
                    s.getDurationSeconds(), s.getSessionDate(), s.getCreatedAt());
        }
    }

    /** Os 3 números da tela: sessões hoje, total de sessões e minutos praticados. */
    public record BreathingStatsResponse(
            long todaySessions,
            long totalSessions,
            long totalMinutes
    ) {
    }
}
