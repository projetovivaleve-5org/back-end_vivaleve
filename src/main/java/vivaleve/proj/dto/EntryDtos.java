package vivaleve.proj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.enums.Activity;
import vivaleve.proj.model.enums.Mood;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public final class EntryDtos {

    private EntryDtos() {
    }

    public record EntryRequest(
            @NotNull Long userId,
            @NotNull Mood mood,
            Set<Activity> activities,
            @Size(max = 1000) String note,
            @NotNull @PastOrPresent LocalDate date
    ) {
    }

    public record EntryResponse(
            Long id,
            Mood mood,
            Set<Activity> activities,
            String note,
            LocalDate date,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** Converte a entidade do banco no JSON de resposta (sem expor o usuário). */
        public static EntryResponse from(EntryEntity entry) {
            return new EntryResponse(
                    entry.getId(),
                    entry.getMood(),
                    Set.copyOf(entry.getActivities()),
                    entry.getNote(),
                    entry.getDate(),
                    entry.getCreatedAt(),
                    entry.getUpdatedAt()
            );
        }
    }
}
