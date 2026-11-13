package vivaleve.proj.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vivaleve.proj.model.MoodLogEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoodLogRepository extends JpaRepository<MoodLogEntity, Long> {

    Optional<MoodLogEntity> findByUserIdAndLogDate(Long userId, LocalDate date);

    boolean existsByUserIdAndLogDate(Long userId, LocalDate date);

    long countByUserIdAndLogDate(Long userId, LocalDate date);

    // Paginação
    Page<MoodLogEntity> findByUserIdOrderByLogDateDesc(
            Long userId,
            Pageable pageable
    );

    // Lista completa
    @EntityGraph(attributePaths = "user")
    List<MoodLogEntity> findByUserIdOrderByLogDateDesc(Long userId);

    // Histórico: busca datas distintas dos registros
    @Query("""
            SELECT DISTINCT m.logDate
            FROM MoodLogEntity m
            WHERE m.user.id = :userId
            ORDER BY m.logDate DESC
            """)
    List<LocalDate> findDistinctDates(@Param("userId") Long userId);

    // Histórico: conta todos os registros do usuário
    long countByUserId(Long userId);

    // Histórico: busca registros dentro de um período
    List<MoodLogEntity> findByUserIdAndLogDateBetweenOrderByLogDateAscCreatedAtAsc(
            Long userId,
            LocalDate start,
            LocalDate end
    );

    // Estatísticas: quantidade de registros por tipo de humor
    @Query("""
            SELECT m.mood, COUNT(m)
            FROM MoodLogEntity m
            WHERE m.user.id = :userId
            AND m.logDate BETWEEN :start AND :end
            GROUP BY m.mood
            """)
    List<Object[]> countByMood(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // Consulta registros de uma data específica
    List<MoodLogEntity> findAllByUserIdAndLogDateOrderByCreatedAtDesc(
            Long userId,
            LocalDate logDate
    );
}
