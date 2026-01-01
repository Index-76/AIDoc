package com.project.aidoc.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long userid;
    private String username;
    private String email;
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer status; // 1-正常, 0-禁用

    public User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}