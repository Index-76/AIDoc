package com.project.aidoc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * 用户实体类 - MongoDB 版本
 */
@Data
@Document(collection = "users")
public class User {
    @Id
    private String id; // MongoDB 使用 String 类型的 ID
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer status; // 1-正常，0-禁用

    public User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
