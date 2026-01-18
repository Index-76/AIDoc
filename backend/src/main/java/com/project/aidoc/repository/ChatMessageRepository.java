package com.project.aidoc.repository;

import com.project.aidoc.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);
    List<ChatMessage> findByUserIdAndSessionIdOrderByTimestampAsc(Long userId, String sessionId);
    void deleteByUserId(Long userId);
}