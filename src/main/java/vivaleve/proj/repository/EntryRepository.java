package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.EntryEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<EntryEntity, Long> {

    /**
     * Busca um registro só se ele pertencer ao usuário informado
     * (SQL gerado: ... WHERE id = ? AND usuario_id = ?). Base da checagem de dono no EntryService.
     */
    Optional<EntryEntity> findByIdAndUser_Id(Long id, Long userId);

    /** Lista todos os registros do usuário, do mais recente para o mais antigo. */
    List<EntryEntity> findByUser_IdOrderByDateDesc(Long userId);

    /** Lista os registros do usuário entre duas datas (inclusive), do mais antigo para o mais recente. */
    List<EntryEntity> findByUser_IdAndDateBetweenOrderByDateAsc(Long userId, LocalDate start, LocalDate end);

    /** Retorna apenas as datas (sem repetição) em que o usuário fez algum registro. Usado no cálculo do streak. */
    @Query("select distinct e.date from EntryEntity e where e.user.id = :userId")
    List<LocalDate> findDistinctDatesByUserId(@Param("userId") Long userId);
}
