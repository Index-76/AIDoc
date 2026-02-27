package com.project.aidoc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API服务类
 */
@Slf4j
@Service
public class ApiService {

    private final RestTemplate restTemplate;

    public ApiService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 调用外部API
     * 
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param prompt 提示词
     * @param model  模型名称
     * @return API响应结果
     */
    public String callExternalApi(String apiUrl, String apiKey, String prompt, String model) {
        try {
            // 输入验证
            if (apiUrl == null || apiUrl.isEmpty()) {
                log.error("API URL is null or empty");
                return "";
            }

            if (apiKey == null || apiKey.isEmpty()) {
                log.error("API key is null or empty");
                return "";
            }

            if (prompt == null || prompt.isEmpty()) {
                log.error("Prompt is null or empty");
                return "";
            }

            // 准备请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建消息
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(message));
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);

            // 发送请求
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

            // 解析响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    return (String) messageObj.get("content");
                }
            }

            log.warn("API调用失败，状态码: {}", response.getStatusCode());
            return "";

        } catch (Exception e) {
            log.error("调用外部API失败", e);
            return "";
        }
    }
}