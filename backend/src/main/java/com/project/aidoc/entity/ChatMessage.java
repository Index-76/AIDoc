package com.project.aidoc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Data
public class ChatMessage {
    @Id
    private String id;
    
    private String userId; // 所属用户 ID (String ObjectId)
    private String sessionId; // 会话 ID
    private String content; // 消息内容
    private String senderType; // 发送者类型：'USER' 或 'AI'
    private LocalDateTime timestamp; // 时间戳
    
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }
}