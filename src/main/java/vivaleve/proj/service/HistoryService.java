package vivaleve.proj.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vivaleve.proj.dto.HistoryDto;
import vivaleve.proj.exceptions.BadRequestExceptions;
import vivaleve.proj.model.MoodLogEntity;
import vivaleve.proj.model.enums.MoodType;
import vivaleve.proj.repository.MoodLogRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class HistoryService {

    private static final ZoneId ZONE =
            ZoneId.of("America/Sao_Paulo");

    private final MoodLogRepository moodLogRepository;

    @Transactional(readOnly = true)
    public HistoryDto.SummaryResponse summary(Long userId) {

        List<LocalDate> datesDesc =
                moodLogRepository.findDistinctDates(userId);

        return new HistoryDto.SummaryResponse(
                moodLogRepository.countByUserId(userId),
                currentStreak(datesDesc, LocalDate.now(ZONE)),
                bestStreak(datesDesc),
                0L // TODO: sessões de respiração
        );
    }

    @Transactional(readOnly = true)
    public List<HistoryDto.EvolutionPoint> evolution(
            Long userId, int days) {

        validateDays(days);

        LocalDate end = LocalDate.now(ZONE);
        LocalDate start = end.minusDays(days - 1L);

        Map<LocalDate, List<MoodLogEntity>> byDay =
                moodLogRepository
                        .findByUserIdAndLogDateBetweenOrderByLogDateAscCreatedAtAsc(
                                userId, start, end)
                        .stream()
                        .collect(Collectors.groupingBy(
                                MoodLogEntity::getLogDate
                        ));

        List<HistoryDto.EvolutionPoint> points = new ArrayList<>();

        for (LocalDate date = start;
             !date.isAfter(end);
             date = date.plusDays(1)) {

            List<MoodLogEntity> logs =
                    byDay.getOrDefault(date, Collections.emptyList());

            Double average = logs.isEmpty()
                    ? null
                    : logs.stream()
                    .mapToInt(log -> log.getMood().getScore())
                    .average()
                    .orElse(0.0);

            points.add(new HistoryDto.EvolutionPoint(
                    date,
                    average,
                    logs.size()
            ));
        }

        return points;
    }

    @Transactional(readOnly = true)
    public HistoryDto.DistributionResponse distribution(
            Long userId, int days) {

        validateDays(days);

        LocalDate end = LocalDate.now(ZONE);
        LocalDate start = end.minusDays(days - 1L);

        Map<MoodType, Long> counts =
                new EnumMap<>(MoodType.class);

        for (MoodType mood : MoodType.values()) {
            counts.put(mood, 0L);
        }

        long total = 0;

        for (Object[] row :
                moodLogRepository.countByMood(userId, start, end)) {

            MoodType mood = (MoodType) row[0];
            long quantity = ((Number) row[1]).longValue();

            counts.put(mood, quantity);
            total += quantity;
        }

        final long totalRecords = total;

        List<HistoryDto.DistributionItem> items =
                counts.entrySet()
                        .stream()
                        .map(entry -> new HistoryDto.DistributionItem(
                                entry.getKey(),
                                entry.getValue(),
                                totalRecords == 0
                                        ? 0.0
                                        : Math.round(
                                        entry.getValue() * 1000.0
                                                / totalRecords
                                ) / 10.0
                        ))
                        .toList();

        HistoryDto.DistributionItem predominant =
                items.stream()
                        .max(Comparator.comparingLong(
                                HistoryDto.DistributionItem::count
                        ))
                        .filter(item -> item.count() > 0)
                        .orElse(null);

        return new HistoryDto.DistributionResponse(
                items,
                total,
                predominant == null ? null : predominant.mood(),
                predominant == null ? 0.0 : predominant.percentage()
        );
    }

    // Sequência atual de dias com registros
    private int currentStreak(
            List<LocalDate> datesDesc, LocalDate today) {

        if (datesDesc.isEmpty()) {
            return 0;
        }

        LocalDate expected = datesDesc.get(0);

        // A sequência permanece ativa se houve registro hoje ou ontem
        if (!expected.equals(today)
                && !expected.equals(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;

        for (LocalDate date : datesDesc) {

            if (!date.equals(expected)) {
                break;
            }

            streak++;
            expected = expected.minusDays(1);
        }

        return streak;
    }

    // Maior sequência histórica de dias consecutivos
    private int bestStreak(List<LocalDate> datesDesc) {

        int best = 0;
        int run = 0;

        LocalDate previous = null;

        for (LocalDate date : datesDesc) {

            if (previous != null
                    && previous.minusDays(1).equals(date)) {
                run++;
            } else {
                run = 1;
            }

            best = Math.max(best, run);
            previous = date;
        }

        return best;
    }

    private void validateDays(int days) {

        if (days < 1 || days > 365) {
            throw new BadRequestExceptions(
                    "Período deve estar entre 1 e 365 dias"
            );
        }
    }
}
