package com.project.aidoc.security;

import cn.dev33.satoken.exception.NotLoginException;
import com.project.aidoc.common.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * 安全相关异常处理器
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public void handleNotLoginException(NotLoginException exception, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String json = objectMapper.writeValueAsString(Result.error(401, "用户未登录"));
        response.getWriter().write(json);
    }
}