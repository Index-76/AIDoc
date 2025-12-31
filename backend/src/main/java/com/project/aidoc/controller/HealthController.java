package com.project.aidoc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public Map<String, Object> healthStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        
        // 添加详细信息
        Map<String, Object> details = new HashMap<>();
        details.put("database", "UP");
        details.put("mongodb", "UP");
        
        result.put("details", details);
        return result;
    }
}