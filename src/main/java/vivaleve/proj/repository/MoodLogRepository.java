package vivaleve.proj.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vivaleve.proj.model.MoodLogEntity;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface MoodLogRepository extends JpaRepository<MoodLogEntity, Long> {

    Optional<MoodLogEntity> findByUserIdAndLogDate(Long userId, LocalDate date);

    boolean existsByUserIdAndLogDate(Long userId, LocalDate date);

    long countByUserIdAndLogDate(Long userId, LocalDate date);

    // paginado (usado no history)
    Page<MoodLogEntity> findByUserIdOrderByLogDateDesc(Long userId, Pageable pageable);

    // lista completa (usado no listAll)
    @EntityGraph(attributePaths = "user")
    List<MoodLogEntity> findByUserIdOrderByLogDateDesc(Long userId);

    // útil para estatísticas (aba Histórico)
    @Query("""
                SELECT m.mood, COUNT(m) FROM MoodLogEntity m
                WHERE m.user.id = :userId AND m.logDate BETWEEN :start AND :end
                GROUP BY m.mood
            """)
    List<Object[]> countByMood(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    List<MoodLogEntity> findAllByUserIdAndLogDateOrderByCreatedAtDesc(
            Long userId,
            LocalDate logDate
    );
}
