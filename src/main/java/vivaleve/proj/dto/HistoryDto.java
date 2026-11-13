package vivaleve.proj.dto;

import vivaleve.proj.model.enums.MoodType;

import java.time.LocalDate;
import java.util.List;

public class HistoryDto {

    public record SummaryResponse(long totalRecords, int currentStreak,
                                  int bestStreak, long breathingSessions) {}

    // averageScore é null nos dias sem registro
    public record EvolutionPoint(LocalDate date, Double averageScore, int count) {}

    public record DistributionItem(MoodType mood, long count, double percentage) {}

    public record DistributionResponse(List<DistributionItem> items, long total,
                                       MoodType predominant, double predominantPercentage) {}
}
