package com.project.aidoc.repository;

import com.project.aidoc.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(String userId);

    List<ChatMessage> findByUserIdAndSessionIdOrderByTimestampAsc(String userId, String sessionId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndSessionId(String userId, String sessionId);
}
