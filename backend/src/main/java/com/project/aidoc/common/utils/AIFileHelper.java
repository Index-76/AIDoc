package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI 决策与文本文件池管理工具类
 * 包含：文件映射决策、文本文件准备与获取、文件摘要等
 */
public class AIFileHelper {
    private static final Logger log = LoggerFactory.getLogger(AIFileHelper.class);
    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY = 1000;

    // ==================== 文件摘要类 ====================
    public static class FileSummary {
        private String fileId;
        private String fileName;
        private String summary;      // 前 200 字符
        private List<String> keywords;
        private String fileType;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
    }

    // ==================== 文本文件池管理 ====================
    public static List<FileSummary> prepareTextFiles(List<File> readFiles, String userId,
                                                      FileService fileService) throws Exception {
        log.info("========== 开始准备全局文本文件池 ==========");
        List<FileSummary> summaries = new ArrayList<>();
        for (File readFile : readFiles) {
            try {
                String targetFileName = readFile.getId() + "_text.txt";
                File textFile = findTextFile(readFile.getId(), targetFileName, userId, fileService);
                if (textFile == null) {
                    log.info("未找到文本文件，开始转换：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
                    byte[] originalContent = fileService.getFileContent(readFile.getId(), userId);
                    if (originalContent == null) continue;
                    String textContent = ExtractText.convertToText(originalContent, readFile.getOriginalName());
                    MultipartFile txtFile = JsonToExcelWriterUtil.createMultipartFile(
                            targetFileName, targetFileName, "text/plain",
                            textContent.getBytes(StandardCharsets.UTF_8));
                    textFile = fileService.saveFile(txtFile, "temp", userId);
                    log.info("文本文件已创建：{} (ID: {})", targetFileName, textFile.getId());
                }
                FileSummary summary = generateFileSummary(readFile, textFile, userId, fileService);
                summaries.add(summary);
            } catch (Exception e) {
                log.error("处理读取文件失败：{} - {}", readFile.getOriginalName(), e.getMessage());
            }
        }
        log.info("========== 文本文件池准备完成，共 {} 个文件 ==========", summaries.size());
        return summaries;
    }

    public static String getTextFileContent(String readFileId, String userId, FileService fileService) throws Exception {
        String targetFileName = readFileId + "_text.txt";
        File textFile = findTextFile(readFileId, targetFileName, userId, fileService);
        if (textFile == null) throw new Exception("未找到文本文件：" + targetFileName);
        byte[] content = fileService.getFileContent(textFile.getId(), userId);
        if (content == null) throw new Exception("读取文本文件内容失败：" + textFile.getId());
        return new String(content, StandardCharsets.UTF_8);
    }

    private static File findTextFile(String readFileId, String targetFileName, String userId, FileService fileService) {
        try {
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");
            for (File f : tempFiles) {
                if (targetFileName.equals(f.getFileName()) || targetFileName.equals(f.getOriginalName()))
                    return f;
            }
            List<File> resultFiles = fileService.getFilesByUserIdAndSection(userId, "result");
            for (File f : resultFiles) {
                if (targetFileName.equals(f.getFileName()) || targetFileName.equals(f.getOriginalName()))
                    return f;
            }
        } catch (Exception e) {
            log.error("查找文本文件失败：{}", e.getMessage());
        }
        return null;
    }

    private static FileSummary generateFileSummary(File readFile, File textFile, String userId, FileService fileService) throws Exception {
        FileSummary summary = new FileSummary();
        summary.setFileId(readFile.getId());
        summary.setFileName(readFile.getOriginalName());
        summary.setFileType(getFileType(readFile.getOriginalName()));

        byte[] content = fileService.getFileContent(textFile.getId(), userId);
        if (content != null) {
            String text = new String(content, StandardCharsets.UTF_8);
            summary.setSummary(text.length() > 200 ? text.substring(0, 200) : text);

            List<String> keywords = new ArrayList<>();
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < Math.min(10, lines.length); i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    String[] words = line.split("[\\s,，。；：！]+");
                    for (String w : words) {
                        if (!w.isEmpty() && w.length() > 1 && keywords.size() < 10) {
                            keywords.add(w);
                        }
                    }
                }
            }
            summary.setKeywords(keywords);
        }
        return summary;
    }

    private static String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "Word";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "Excel";
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".csv")) return "CSV";
        return "unknown";
    }

    // ==================== AI 决策文件映射 ====================
    public static Map<Integer, List<String>> decideFileMapping(
            List<Map<String, Object>> tablesOrSheets,
            List<FileSummary> fileSummaries,
            String userDescription,
            String userId,
            UserConfigService userConfigService,
            ApiService apiService) throws Exception {
        log.info("========== 开始 AI 决策文件映射 ==========");
        if (fileSummaries.isEmpty()) {
            log.warn("没有可用的读取文件，将返回空映射");
            return new HashMap<>();
        }

        UserConfig userConfig = userConfigService.getUserConfig(userId);
        String apiKey = (userConfig != null) ? userConfig.getSiliconFlowApiKey() : null;
        String apiUrl = (userConfig != null && userConfig.getSiliconFlowBaseUrl() != null)
                ? userConfig.getSiliconFlowBaseUrl() : "https://api.siliconflow.com/v1";
        String modelName = (userConfig != null && userConfig.getDecisionModelName() != null)
                ? userConfig.getDecisionModelName() : "Qwen/Qwen2.5-7B-Instruct";

        if (apiKey == null || apiKey.isEmpty()) {
            log.error("未配置 API Key，降级为关键词匹配");
            return keywordBasedMapping(tablesOrSheets, fileSummaries);
        }

        try {
            String prompt = buildMappingPrompt(tablesOrSheets, fileSummaries, userDescription);
            String aiResponse = callAIWithRetry(prompt, apiUrl, apiKey, modelName, apiService);
            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                log.warn("AI 返回空响应，降级为关键词匹配");
                return keywordBasedMapping(tablesOrSheets, fileSummaries);
            }
            Map<Integer, List<String>> mapping = parseAIMappingResponse(aiResponse, fileSummaries);
            mapping = validateAndCleanMapping(mapping, fileSummaries, tablesOrSheets.size());
            log.info("AI 决策完成，映射关系：{}", mapping);
            return mapping;
        } catch (Exception e) {
            log.error("AI 决策失败，降级为关键词匹配：{}", e.getMessage());
            return keywordBasedMapping(tablesOrSheets, fileSummaries);
        }
    }

    private static String callAIWithRetry(String prompt, String apiUrl, String apiKey, String modelName,
                                           ApiService apiService) throws Exception {
        long waitTime = INITIAL_RETRY_DELAY;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            try {
                String response = apiService.callExternalApi(apiUrl, apiKey, prompt, modelName);
                if (response != null && !response.trim().isEmpty()) return response;
            } catch (Exception e) {
                log.warn("AI 调用失败 (尝试 {}/{}): {}", retry + 1, MAX_RETRIES + 1, e.getMessage());
                if (retry == MAX_RETRIES) throw e;
                Thread.sleep(waitTime);
                waitTime *= 2;
            }
        }
        throw new Exception("所有重试均失败");
    }

    private static String buildMappingPrompt(List<Map<String, Object>> tablesOrSheets,
                                              List<FileSummary> fileSummaries, String userDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能填表助手。请根据以下信息，为每个表格/工作表推荐应当使用的读取文件。\n\n");
        prompt.append("【模板中的表格/工作表信息】\n");
        for (int i = 0; i < tablesOrSheets.size(); i++) {
            Map<String, Object> table = tablesOrSheets.get(i);
            prompt.append("表格").append(i).append(":\n");
            prompt.append("  - 表头：").append(String.join(", ", (List<String>) table.get("headers"))).append("\n");
            if (table.containsKey("sheetDiscription1") && !((String) table.get("sheetDiscription1")).isEmpty())
                prompt.append("  - 上文：").append(table.get("sheetDiscription1")).append("\n");
            if (table.containsKey("sheetDiscription2") && !((String) table.get("sheetDiscription2")).isEmpty())
                prompt.append("  - 下文：").append(table.get("sheetDiscription2")).append("\n");
            if (table.containsKey("sheetName"))
                prompt.append("  - 工作表名：").append(table.get("sheetName")).append("\n");
            prompt.append("\n");
        }
        prompt.append("【可用的读取文件】\n");
        for (FileSummary summary : fileSummaries) {
            prompt.append("文件 ID: ").append(summary.getFileId())
                  .append(", 文件名：").append(summary.getFileName())
                  .append(", 类型：").append(summary.getFileType())
                  .append(", 摘要：").append(summary.getSummary())
                  .append(", 关键词：").append(String.join(", ", summary.getKeywords()))
                  .append("\n");
        }
        prompt.append("\n");
        if (userDescription != null && !userDescription.isEmpty())
            prompt.append("【用户描述】\n").append(userDescription).append("\n\n");
        prompt.append("【任务要求】\n");
        prompt.append("1. 为每个表格推荐 1-3 个最相关的读取文件 ID\n");
        prompt.append("2. 返回 JSON 格式：{ \"0\": [\"fileId1\", \"fileId2\"], \"1\": [\"fileId3\"], ... }\n");
        prompt.append("3. 必须基于表头列名、上下文语义、文件内容进行匹配\n");
        prompt.append("4. 只返回提供的文件 ID 列表中存在的 ID\n");
        prompt.append("5. 如果某个表格没有合适的文件，可以返回空列表 []\n");
        prompt.append("6. 直接返回 JSON，不要有任何说明文字\n");
        return prompt.toString();
    }

    private static Map<Integer, List<String>> parseAIMappingResponse(String aiResponse,
                                                                       List<FileSummary> fileSummaries) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> validFileIds = new HashSet<>();
        for (FileSummary s : fileSummaries) validFileIds.add(s.getFileId());

        String jsonContent = extractJsonFromResponse(aiResponse);
        if (jsonContent == null || jsonContent.trim().isEmpty())
            throw new Exception("无法从 AI 响应中提取 JSON");

        JsonNode rootNode = mapper.readTree(jsonContent);
        Map<Integer, List<String>> mapping = new HashMap<>();
        rootNode.fields().forEachRemaining(entry -> {
            try {
                int idx = Integer.parseInt(entry.getKey());
                List<String> ids = new ArrayList<>();
                if (entry.getValue().isArray()) {
                    entry.getValue().forEach(node -> {
                        String fileId = node.asText();
                        if (validFileIds.contains(fileId)) ids.add(fileId);
                        else log.warn("AI 返回了无效的文件 ID: {}", fileId);
                    });
                }
                mapping.put(idx, ids);
            } catch (NumberFormatException e) {
                log.warn("AI 返回了非数字的表格索引：{}", entry.getKey());
            }
        });
        return mapping;
    }

    private static Map<Integer, List<String>> validateAndCleanMapping(
            Map<Integer, List<String>> mapping, List<FileSummary> fileSummaries, int totalTables) {
        Set<String> valid = new HashSet<>();
        for (FileSummary s : fileSummaries) valid.add(s.getFileId());
        Map<Integer, List<String>> cleaned = new HashMap<>();
        for (int i = 0; i < totalTables; i++) {
            List<String> ids = mapping.getOrDefault(i, new ArrayList<>());
            ids.removeIf(id -> !valid.contains(id));
            if (ids.isEmpty()) {
                log.warn("表格 {} 没有匹配到任何文件，将使用所有文件作为备选", i);
                for (FileSummary s : fileSummaries) ids.add(s.getFileId());
            }
            cleaned.put(i, ids);
        }
        return cleaned;
    }

    private static Map<Integer, List<String>> keywordBasedMapping(
            List<Map<String, Object>> tablesOrSheets, List<FileSummary> fileSummaries) {
        Map<Integer, List<String>> mapping = new HashMap<>();
        for (int i = 0; i < tablesOrSheets.size(); i++) {
            Map<String, Object> table = tablesOrSheets.get(i);
            List<String> headers = (List<String>) table.get("headers");
            List<String> matched = new ArrayList<>();
            for (FileSummary s : fileSummaries) {
                boolean match = false;
                String lowerFileName = s.getFileName().toLowerCase();
                for (String h : headers) {
                    if (h != null && lowerFileName.contains(h.toLowerCase())) { match = true; break; }
                }
                if (!match && s.getSummary() != null) {
                    String lowerSum = s.getSummary().toLowerCase();
                    for (String h : headers) {
                        if (h != null && lowerSum.contains(h.toLowerCase())) { match = true; break; }
                    }
                }
                if (!match && s.getKeywords() != null) {
                    for (String kw : s.getKeywords()) {
                        for (String h : headers) {
                            if (h != null && kw.equalsIgnoreCase(h)) { match = true; break; }
                        }
                        if (match) break;
                    }
                }
                if (match) matched.add(s.getFileId());
            }
            if (matched.isEmpty()) {
                log.warn("表格 {} 关键词匹配失败，将使用所有文件", i);
                for (FileSummary s : fileSummaries) matched.add(s.getFileId());
            }
            mapping.put(i, matched);
        }
        return mapping;
    }

    private static String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        String markdownCleaned = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (markdownCleaned.startsWith("{") && markdownCleaned.endsWith("}")) return markdownCleaned;
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : null;
    }
}