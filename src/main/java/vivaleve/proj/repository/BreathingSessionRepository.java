package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.BreathingSessionEntity;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BreathingSessionRepository extends JpaRepository<BreathingSessionEntity, Long> {

    /** Sessões do usuário, da mais recente para a mais antiga. */
    List<BreathingSessionEntity> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUser_Id(Long userId);

    long countByUser_IdAndSessionDate(Long userId, LocalDate sessionDate);

    /** Soma dos segundos de todas as sessões do usuário (0 se não tiver nenhuma). */
    @Query("select coalesce(sum(s.durationSeconds), 0) from BreathingSessionEntity s where s.user.id = :userId")
    long sumDurationSecondsByUserId(@Param("userId") Long userId);

    /** Apaga o histórico do usuário (botão "Limpar" da tela). Devolve quantas foram apagadas. */
    @Modifying
    @Query("delete from BreathingSessionEntity s where s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
