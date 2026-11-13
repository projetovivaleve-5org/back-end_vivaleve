package vivaleve.proj.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.AiMessageEntity;

import java.util.List;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessageEntity, Long> {

    /** Todas as mensagens da conversa, na ordem em que foram criadas. */
    List<AiMessageEntity> findByConversation_IdOrderByIdAsc(Long conversationId);

    /** Últimas N mensagens (da mais nova para a mais antiga) — usado como memória do chat. */
    List<AiMessageEntity> findByConversation_IdOrderByIdDesc(Long conversationId, Pageable pageable);

    /** Apaga todas as mensagens da conversa (usado antes de apagar a conversa). */
    @Modifying
    @Query("delete from AiMessageEntity m where m.conversation.id = :conversationId")
    void deleteAllByConversationId(@Param("conversationId") Long conversationId);
}
