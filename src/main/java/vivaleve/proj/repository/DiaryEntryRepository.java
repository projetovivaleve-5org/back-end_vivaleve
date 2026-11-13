package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.DiaryEntryEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryEntryRepository extends JpaRepository<DiaryEntryEntity, Long> {

    /** Busca uma entrada só se ela pertencer ao usuário (base da checagem de dono no DiaryService). */
    Optional<DiaryEntryEntity> findByIdAndUser_Id(Long id, Long userId);

    /** Entradas do usuário, da mais recente para a mais antiga. */
    List<DiaryEntryEntity> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);
}
