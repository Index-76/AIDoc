package com.project.aidoc.common.dto;

import lombok.Data;
import com.project.aidoc.entity.User;

@Data
public class LoginResponse {
    private String token;
    private User user;
    private String message;

    public LoginResponse(String token, User user, String message) {
        this.token = token;
        this.user = user;
        this.message = message;
    }
}