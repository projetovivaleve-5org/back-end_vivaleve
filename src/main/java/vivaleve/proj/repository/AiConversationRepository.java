package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.AiConversationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversationEntity, Long> {

    /** Conversas do usuário, da mais recente para a mais antiga. */
    List<AiConversationEntity> findByUser_IdOrderByLastMessageAtDesc(Long userId);

    /** Busca a conversa garantindo que ela pertence ao usuário. */
    Optional<AiConversationEntity> findByIdAndUser_Id(Long id, Long userId);
}
