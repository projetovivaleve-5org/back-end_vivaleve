package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.EntryEntity;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<EntryEntity, Long> {

    /** Lista todos os registros do usuário, do mais recente para o mais antigo. */
    List<EntryEntity> findByUser_IdOrderByDateDesc(Long userId);

    /** Lista os registros do usuário entre duas datas (inclusive), do mais antigo para o mais recente. */
    List<EntryEntity> findByUser_IdAndDateBetweenOrderByDateAsc(Long userId, LocalDate start, LocalDate end);

    /** Retorna apenas as datas (sem repetição) em que o usuário fez algum registro. Usado no cálculo do streak. */
    @Query("select distinct e.date from EntryEntity e where e.user.id = :userId")
    List<LocalDate> findDistinctDatesByUserId(@Param("userId") Long userId);
}
