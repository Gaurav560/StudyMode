package com.telusko.studymode.repo;


import com.telusko.studymode.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<ConversationEntity> findByConversationIdAndUserId(String conversationId, String userId);

    void deleteByConversationIdAndUserId(String conversationId, String userId);

    void deleteByUserId(String userId);
}
