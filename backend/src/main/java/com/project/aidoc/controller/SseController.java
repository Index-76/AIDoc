package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.service.SseService;
import com.project.aidoc.utils.SseAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sse")
public class SseController {

    @Autowired
    private SseService sseService;

    @GetMapping(value = "/connect/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connect(@PathVariable("sessionId") String sessionId) {
        // 登录验证
        Result<Void> authResult = SseAuthUtil.checkLogin();
        if (authResult != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        log.info("SSE Connect: {}", sessionId);
        SseEmitter emitter = sseService.createConnection(sessionId);

        // 如果创建失败
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        return ResponseEntity.ok(emitter);
    }

    @PostMapping("/disconnect/{sessionId}")
    public ResponseEntity<Result<Void>> disconnect(@PathVariable("sessionId") String sessionId) {
        // 登录验证
        Result<Void> authResult = SseAuthUtil.checkLogin();
        if (authResult != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResult);
        }

        log.info("SSE Disconnect: {}", sessionId);
        sseService.closeConnection(sessionId);

        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/connections")
    public ResponseEntity<Result<Integer>> getConnectionCount() {
        // 登录验证
        Result<Void> authResult = SseAuthUtil.checkLogin();
        if (authResult != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Result<>(authResult.getCode(), authResult.getMsg()));
        }

        int count = sseService.getConnectionCount();
        log.info("SSE Connections Count: {}", count);

        return ResponseEntity.ok(Result.success(count));
    }
}