package com.project.aidoc.common.tools;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.FileRepository;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容总结工具类
 * 用于总结文件内容并处理缓存
 */
@Slf4j
@Component
public class ContentSummarizerTool {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserConfigService userConfigService;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 总结文件内容
     * 
     * @param filePath            文件路径
     * @param summaryRequirements 总结要求
     * @param userId              用户 ID (String ObjectId)
     * @return 总结内容 Map
     */
    public Map<String, Object> summarizeContent(String filePath, String summaryRequirements, String userId) {
        log.info("开始总结内容：filePath={}, requirements={}, userId={}", filePath, summaryRequirements, userId);

        try {
            // 从 filePath 中提取源文件 ID
            String sourceFileId = extractFileIdFromPath(filePath);
            if (sourceFileId == null || sourceFileId.isEmpty()) {
                return createErrorResult("无法从文件路径中提取文件 ID: " + filePath);
            }

            // 构造缓存文件名：[文件 id]_summary.json
            String cacheFileName = sourceFileId + "_summary.json";

            // 1. 首先尝试从 temp 区域查找缓存的总结文件
            File cachedSummary = findCachedSummary(sourceFileId, cacheFileName, userId);

            if (cachedSummary != null) {
                log.info("找到缓存的总结文件：{}", cachedSummary.getId());
                // 2. 如果有缓存，直接返回缓存内容
                String cachedContent = getFileContentAsString(cachedSummary.getId(), userId);
                if (cachedContent != null && !cachedContent.isEmpty()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("message", "从缓存获取总结内容成功");
                    result.put("isCache", true);
                    result.put("content", parseJsonContent(cachedContent));
                    result.put("cacheFileId", cachedSummary.getId());
                    return result;
                }
            }

            // 3. 如果没有缓存，使用 AI 总结文件内容
            log.info("未找到缓存总结，开始使用 AI 总结文件内容");

            // 获取源文件内容
            byte[] sourceFileContent = fileService.getFileContent(sourceFileId, userId);
            if (sourceFileContent == null || sourceFileContent.length == 0) {
                return createErrorResult("无法读取源文件内容");
            }

            String sourceContent = new String(sourceFileContent, StandardCharsets.UTF_8);

            // 调用 AI 进行总结
            String aiSummary = callAiForSummary(sourceContent, summaryRequirements, userId);
            if (aiSummary == null || aiSummary.isEmpty()) {
                return createErrorResult("AI 总结失败");
            }

            // 4. 将总结内容保存到 temp 区域
            File savedSummaryFile = saveSummaryToTemp(sourceFileId, cacheFileName, aiSummary, userId);

            // 5. 返回总结内容
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "内容总结成功");
            result.put("isCache", false);
            result.put("content", parseJsonContent(aiSummary));
            result.put("cacheFileId", savedSummaryFile != null ? savedSummaryFile.getId() : null);

            log.info("内容总结完成，已保存到 temp 区域");
            return result;

        } catch (Exception e) {
            log.error("内容总结失败", e);
            return createErrorResult("内容总结失败：" + e.getMessage());
        }
    }

    /**
     * 从文件路径中提取文件 ID
     * 路径格式：/api/v1/files/{fileId}/download
     */
    private String extractFileIdFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        // 尝试从路径中提取 ObjectId
        String[] parts = filePath.split("/");
        for (String part : parts) {
            if (part.length() > 10 && part.matches("^[a-f0-9]{24}$")) {
                return part;
            }
        }

        // 如果路径本身就是 fileId
        if (filePath.length() > 10 && filePath.matches("^[a-f0-9]{24}$")) {
            return filePath;
        }

        return null;
    }

    /**
     * 在 temp 区域查找缓存的总结文件
     */
    private File findCachedSummary(String sourceFileId, String cacheFileName, String userId) {
        try {
            // 获取当前用户的所有 temp 区域文件
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");

            // 查找匹配的总结缓存文件
            for (File file : tempFiles) {
                if (file.getFileName().equals(cacheFileName)) {
                    return file;
                }
            }

            return null;
        } catch (Exception e) {
            log.error("查找缓存总结文件失败", e);
            return null;
        }
    }

    /**
     * 获取文件内容并转换为字符串
     */
    private String getFileContentAsString(String fileId, String userId) {
        try {
            byte[] content = fileService.getFileContent(fileId, userId);
            if (content != null && content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
            return null;
        } catch (Exception e) {
            log.error("读取文件内容失败：{}", fileId, e);
            return null;
        }
    }

    /**
     * 解析 JSON 内容（简单的处理，如果内容是 JSON 格式则解析，否则直接返回）
     */
    private Object parseJsonContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 尝试判断是否为 JSON 格式
        String trimmed = content.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            log.debug("检测到 JSON 格式内容，长度：{}", content.length());
            return content;
        }

        // 如果不是 JSON 格式，包装成简单的对象
        Map<String, String> simpleResult = new HashMap<>();
        simpleResult.put("summary", content);
        return simpleResult;
    }

    /**
     * 调用 AI 获取总结内容
     *
     * @param content     文件内容
     * @param requirements 总结要求
     * @param userId      用户 ID (String ObjectId)
     * @return AI 生成的总结内容
     */
    private String callAiForSummary(String content, String requirements, String userId) {
        try {
            // 构建提示词
            String prompt = buildSummaryPrompt(content, requirements);

            // 从用户配置中获取 API 配置
            UserConfig config = userConfigService.getUserConfig(userId);

            String apiKey = null;
            String apiUrl = "https://api.siliconflow.cn/v1/chat/completions"; // 默认值
            String model = "deepseek-ai/DeepSeek-V3.2"; // 默认值

            if (config != null) {
                apiKey = config.getSiliconFlowApiKey();
                if (config.getSiliconFlowBaseUrl() != null && !config.getSiliconFlowBaseUrl().isEmpty()) {
                    apiUrl = config.getSiliconFlowBaseUrl();
                }
                if (config.getAnalysisModelName() != null && !config.getAnalysisModelName().isEmpty()) {
                    model = config.getAnalysisModelName();
                }
            }

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("用户 {} 未配置 API Key，使用默认总结", userId);
                return generateDefaultSummary(content);
            }

            log.info("使用 API 配置 - URL: {}, Model: {}", apiUrl, model);

            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    String aiContent = (String) messageObj.get("content");

                    // 确保换行符被正确处理
                    log.info("AI 返回内容长度：{}, 包含换行符：{}", aiContent.length(), aiContent.contains("\n"));
                    return aiContent;
                }
            }

            log.warn("AI 调用失败，使用默认总结");
            return generateDefaultSummary(content);

        } catch (Exception e) {
            log.error("调用 AI 总结失败", e);
            return generateDefaultSummary(content);
        }
    }

    /**
     * 构建总结提示词
     */
    private String buildSummaryPrompt(String content, String requirements) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下文档内容进行总结：\n\n");
        prompt.append(content);
        prompt.append("\n\n总结要求：").append(requirements);
        prompt.append("\n\n请将总结结果以 JSON 格式返回，包含以下字段：");
        prompt.append("{\"title\": \"文档标题\", \"keyPoints\": [\"关键点 1\", \"关键点 2\", ...], \"summary\": \"详细总结内容\"}");
        return prompt.toString();
    }

    /**
     * 生成默认总结（当 AI 不可用时）
     */
    private String generateDefaultSummary(String content) {
        // 简单的文本摘要：取前 500 个字符
        if (content == null || content.isEmpty()) {
            return "{\"title\": \"无标题\", \"keyPoints\": [], \"summary\": \"内容为空\"}";
        }

        String truncated = content.length() > 500 ? content.substring(0, 500) + "..." : content;
        Map<String, String> defaultSummary = new HashMap<>();
        defaultSummary.put("title", "文档摘要");
        defaultSummary.put("keyPoints", "自动提取的关键点");
        defaultSummary.put("summary", truncated);

        return "{\"title\": \"文档摘要\", \"summary\": \"" + truncated.replace("\"", "\\\"") + "\"}";
    }

    /**
     * 将总结内容保存到 temp 区域
     * 
     * @param sourceFileId   源文件ID
     * @param cacheFileName  缓存文件名
     * @param summaryContent 总结内容
     * @param userId         用户ID
     * @return 保存后的文件实体
     */
    private File saveSummaryToTemp(String sourceFileId, String cacheFileName, String summaryContent, String userId) {
        try {
            // 生成原始名称
            String originalName = "[" + sourceFileId + "]_summary.json";

            // 将 JSON 内容存储到 GridFS
            byte[] contentBytes = summaryContent.getBytes(StandardCharsets.UTF_8);
            ObjectId textObjectId = gridFsTemplate.store(
                    new ByteArrayInputStream(contentBytes),
                    cacheFileName,
                    "application/json");

            // 创建文件实体
            File summaryFileEntity = new File();
            summaryFileEntity.setId(textObjectId.toString());
            summaryFileEntity.setFileName(cacheFileName);
            summaryFileEntity.setOriginalName(originalName);
            summaryFileEntity.setContentType("application/json");
            summaryFileEntity.setSize(contentBytes.length);
            summaryFileEntity.setSection("temp");
            summaryFileEntity.setUserId(userId);
            summaryFileEntity.setUploadTime(LocalDateTime.now());
            summaryFileEntity.setFilePath("/api/v1/files/" + textObjectId.toString() + "/download");

            // 保存到 MongoDB
            return fileRepository.save(summaryFileEntity);

        } catch (Exception e) {
            log.error("保存总结文件到 temp 区域失败", e);
            return null;
        }
    }

    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "failed");
        result.put("message", errorMessage);
        result.put("isCache", false);
        result.put("content", null);
        return result;
    }

    /**
     * 获取 API Key（从用户配置中获取）
     * 
     * @param userId 用户 ID (String ObjectId)
     * @return API 密钥，如果未配置则返回 null
     */
    private String getApiKey(String userId) {
        // 从 MongoDB 中获取用户的配置
        UserConfig config = userConfigService.getUserConfig(userId);
        if (config != null && config.getSiliconFlowApiKey() != null && !config.getSiliconFlowApiKey().isEmpty()) {
            return config.getSiliconFlowApiKey();
        }

        // 如果用户没有配置 API Key，返回 null
        log.warn("用户 {} 未配置 SiliconFlow API Key", userId);
        return null;
    }
}
