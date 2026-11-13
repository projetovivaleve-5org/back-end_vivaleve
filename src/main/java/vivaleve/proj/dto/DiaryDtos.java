package vivaleve.proj.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vivaleve.proj.model.DiaryEntryEntity;
import vivaleve.proj.model.enums.MoodType;

import java.time.LocalDateTime;

/** Formato do JSON das rotas do diário ({@code /v1/diary}). */
public final class DiaryDtos {

    private DiaryDtos() {
    }

    /**
     * Corpo de POST/PUT /v1/diary. Não tem userId: o dono vem do JWT.
     * {@code emotion} é opcional (OTIMO, BEM, MAIS_OU_MENOS, MAL, DEPRESSIVO, ANSIOSO).
     */
    public record DiaryRequest(
            @NotBlank(message = "O título é obrigatório")
            @Size(max = 100, message = "O título deve ter no máximo 100 caracteres")
            String title,

            @NotBlank(message = "O texto é obrigatório")
            @Size(max = 5000, message = "O texto deve ter no máximo 5000 caracteres")
            String text,

            MoodType emotion
    ) {
    }

    public record DiaryResponse(
            Long id,
            String title,
            String text,
            MoodType emotion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** Converte a entidade no JSON de resposta (sem expor o usuário). */
        public static DiaryResponse from(DiaryEntryEntity e) {
            return new DiaryResponse(e.getId(), e.getTitle(), e.getText(), e.getEmotion(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
