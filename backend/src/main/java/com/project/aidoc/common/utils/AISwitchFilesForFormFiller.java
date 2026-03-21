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
 * AI 决策与文件池管理工具类（支持文本文件和 Excel 文件）
 * 包含：文件摘要生成、文件内容统一获取、文件映射决策等
 */
public class AISwitchFilesForFormFiller {
    private static final Logger log = LoggerFactory.getLogger(AISwitchFilesForFormFiller.class);
    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY = 1000;

    // 配置：是否保留 Excel 摘要文件（默认 true）
    private static final boolean KEEP_EXCEL_SUMMARY = Boolean.parseBoolean(
            System.getProperty("template.keep.excel.summary", "true"));

    // ==================== 文件摘要类 ====================
    public static class FileSummary {
        private String fileId;
        private String fileName;
        private String summary;           // 摘要（文本文件为前200字符，Excel为简短描述）
        private String fileType;           // 文件类型（PDF, Word, Excel, TXT...）

        // 新增字段（用于Excel）
        private String originalFileId;      // 原始文件ID（同fileId，语义明确）
        private String summaryFilePath;     // 摘要JSON文件ID（仅Excel有效）

        // getters/setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public String getOriginalFileId() { return originalFileId; }
        public void setOriginalFileId(String originalFileId) { this.originalFileId = originalFileId; }
        public String getSummaryFilePath() { return summaryFilePath; }
        public void setSummaryFilePath(String summaryFilePath) { this.summaryFilePath = summaryFilePath; }
    }

    /**
     * 统一文件内容包装类
     */
    public static class FileContentWrapper {
        private byte[] content;          // 文件内容（原始字节或文本字节）
        private String fileType;          // 文件类型（如 "EXCEL", "TEXT", "PDF" 等）
        private String originalFileId;    // 原始文件ID
        private boolean isText;           // 是否为可直接转换为 String 的文本内容

        public FileContentWrapper(byte[] content, String fileType, String originalFileId, boolean isText) {
            this.content = content;
            this.fileType = fileType;
            this.originalFileId = originalFileId;
            this.isText = isText;
        }

        public byte[] getContent() { return content; }
        public String getFileType() { return fileType; }
        public String getOriginalFileId() { return originalFileId; }
        public boolean isText() { return isText; }
    }

    // ==================== 文件池准备 ====================

    /**
     * 统一准备所有读取文件的摘要信息（文本文件生成文本文件，Excel 生成摘要 JSON）
     * @param readFiles 读取区文件列表
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 文件摘要列表
     * @throws Exception 处理失败时抛出
     */
    public static List<FileSummary> prepareFiles(List<File> readFiles, String userId,
                                                 FileService fileService) throws Exception {
        return prepareFiles(readFiles, userId, fileService, false);
    }

    /**
     * 统一准备所有读取文件的摘要信息（支持缓存检查）
     * @param readFiles 读取区文件列表
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param checkCache 是否检查缓存（若为 true，则优先使用 temp 区已有的预处理文件）
     * @return 文件摘要列表
     * @throws Exception 处理失败时抛出
     */
    public static List<FileSummary> prepareFiles(List<File> readFiles, String userId,
                                                 FileService fileService, boolean checkCache) throws Exception {
        log.info("========== 开始准备全局文件池 ==========");
        log.info("缓存检查：{}", checkCache ? "启用" : "禁用");
        List<FileSummary> summaries = new ArrayList<>();

        for (File readFile : readFiles) {
            try {
                String fileId = readFile.getId();
                String fileName = readFile.getOriginalName();
                String fileType = getFileType(fileName);

                if (isExcelFile(fileType)) {
                    // 处理 Excel 文件：生成摘要 JSON 并构建 FileSummary
                    FileSummary summary = prepareExcelFile(readFile, userId, fileService, checkCache);
                    summaries.add(summary);
                } else {
                    // 处理非 Excel 文件：转换为文本文件并生成摘要
                    FileSummary summary = prepareNonExcelFile(readFile, userId, fileService, checkCache);
                    summaries.add(summary);
                }
            } catch (Exception e) {
                log.error("处理读取文件失败：{} - {}", readFile.getOriginalName(), e.getMessage());
            }
        }

        log.info("========== 文件池准备完成，共 {} 个文件 ==========", summaries.size());
        return summaries;
    }

    /**
     * 准备非 Excel 文件（文本文件转换及摘要生成）
     */
    private static FileSummary prepareNonExcelFile(File readFile, String userId,
                                                   FileService fileService, boolean checkCache) throws Exception {
        String targetFileName = readFile.getId() + "_text.txt";
        File textFile = null;

        // 1. 如果启用缓存检查，先查找是否已存在预处理文件
        if (checkCache) {
            textFile = findTextFile(readFile.getId(), targetFileName, userId, fileService);
            if (textFile != null) {
                log.info("缓存命中：找到已存在的文本文件 {} (ID: {})", targetFileName, textFile.getId());
            }
        }

        // 2. 如果未找到或禁用缓存，则生成新的文本文件
        if (textFile == null) {
            log.info("未找到文本文件，开始转换：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
            byte[] originalContent = fileService.getFileContent(readFile.getId(), userId);
            if (originalContent == null) {
                throw new Exception("读取文件内容失败：" + readFile.getId());
            }
            String textContent = ExtractText.convertToText(originalContent, readFile.getOriginalName());
            MultipartFile txtFile = JsonToExcelWriterUtil.createMultipartFile(
                    targetFileName, targetFileName, "text/plain",
                    textContent.getBytes(StandardCharsets.UTF_8));
            textFile = fileService.saveFile(txtFile, "temp", userId);
            log.info("文本文件已创建：{} (ID: {})", targetFileName, textFile.getId());
        }

        // 3. 生成摘要
        FileSummary summary = generateNonExcelFileSummary(readFile, textFile, userId, fileService);
        return summary;
    }

    /**
     * 准备 Excel 文件（生成摘要 JSON）
     */
    private static FileSummary prepareExcelFile(File readFile, String userId,
                                                FileService fileService, boolean checkCache) throws Exception {
        log.info("处理 Excel 文件：{} (ID: {})", readFile.getOriginalName(), readFile.getId());

        String summaryFileName = readFile.getId() + "_summary.json";
        File savedSummaryFile = null;

        // 1. 如果启用缓存检查，先查找是否已存在摘要文件
        if (checkCache) {
            savedSummaryFile = findSummaryFile(readFile.getId(), summaryFileName, userId, fileService);
            if (savedSummaryFile != null) {
                log.info("缓存命中：找到已存在的 Excel 摘要文件 {} (ID: {})", summaryFileName, savedSummaryFile.getId());
            }
        }

        // 2. 如果未找到或禁用缓存，则生成新的摘要文件
        ExcelDataProcessor.ExcelSummary excelSummary;
        if (savedSummaryFile == null) {
            byte[] content = fileService.getFileContent(readFile.getId(), userId);
            if (content == null) {
                throw new Exception("读取 Excel 文件内容失败：" + readFile.getId());
            }

            // 调用 ExcelDataProcessor 生成摘要对象
            excelSummary = ExcelDataProcessor.generateExcelSummary(content, readFile.getId());

            // 将摘要对象保存为 JSON 文件
            ObjectMapper mapper = new ObjectMapper();
            String jsonContent = mapper.writeValueAsString(excelSummary);
            MultipartFile summaryFile = JsonToExcelWriterUtil.createMultipartFile(
                    summaryFileName, summaryFileName, "application/json",
                    jsonContent.getBytes(StandardCharsets.UTF_8));
            savedSummaryFile = fileService.saveFile(summaryFile, "temp", userId);
            log.info("Excel 摘要文件已保存：{} (ID: {})", summaryFileName, savedSummaryFile.getId());
        } else {
            // 从缓存的摘要文件中读取并反序列化
            byte[] jsonBytes = fileService.getFileContent(savedSummaryFile.getId(), userId);
            ObjectMapper mapper = new ObjectMapper();
            excelSummary = mapper.readValue(jsonBytes, ExcelDataProcessor.ExcelSummary.class);
            log.info("已从缓存文件加载 Excel 摘要：{}", summaryFileName);
        }

        // 3. 构建 FileSummary 对象
        FileSummary summary = new FileSummary();
        summary.setFileId(readFile.getId());
        summary.setOriginalFileId(readFile.getId());
        summary.setFileName(readFile.getOriginalName());
        summary.setFileType("Excel");
        summary.setSummaryFilePath(savedSummaryFile.getId());

        // 生成简短的摘要描述（用于 AI 决策）
        StringBuilder summaryBuilder = new StringBuilder("Excel 文件，包含 ")
                .append(excelSummary.getSheetNum()).append(" 个工作表：");
        for (ExcelDataProcessor.ExcelSummary.SheetSummary sheet : excelSummary.getSheets()) {
            summaryBuilder.append(sheet.getSheetName())
                    .append("(表头：").append(String.join(",", sheet.getHeaders())).append("); ");
        }
        summary.setSummary(summaryBuilder.toString());

        return summary;
    }

    /**
     * 查找文本文件（在 temp 和 result 区查找）
     */
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

    /**
     * 查找 Excel 摘要文件（在 temp 和 result 区查找）
     */
    private static File findSummaryFile(String readFileId, String targetFileName, String userId, FileService fileService) {
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
            log.error("查找摘要文件失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 为非 Excel 文件生成摘要（基于文本文件）
     */
    private static FileSummary generateNonExcelFileSummary(File readFile, File textFile,
                                                           String userId, FileService fileService) throws Exception {
        FileSummary summary = new FileSummary();
        summary.setFileId(readFile.getId());
        summary.setOriginalFileId(readFile.getId());
        summary.setFileName(readFile.getOriginalName());
        summary.setFileType(getFileType(readFile.getOriginalName()));

        byte[] content = fileService.getFileContent(textFile.getId(), userId);
        if (content != null) {
            String text = new String(content, StandardCharsets.UTF_8);
            summary.setSummary(text.length() > 200 ? text.substring(0, 200) : text);
        }
        return summary;
    }

    // ==================== 统一获取文件内容 ====================

    /**
     * 统一获取文件内容（文本文件返回文本内容，Excel 返回原始字节）
     * @param fileId 文件ID（原始文件ID）
     * @param userId 用户ID
     * @param fileService 文件服务
     * @return 文件内容包装对象
     * @throws Exception 获取失败时抛出
     */
    public static FileContentWrapper getFileContent(String fileId, String userId,
                                                    FileService fileService) throws Exception {
        // 获取原始文件信息
        File originalFile = fileService.getFileById(fileId, userId);
        if (originalFile == null) {
            throw new Exception("文件不存在：" + fileId);
        }

        String fileType = getFileType(originalFile.getOriginalName());

        if (isExcelFile(fileType)) {
            // Excel 文件直接返回原始字节
            byte[] content = fileService.getFileContent(fileId, userId);
            System.out.println(String.format("文件 ID: %s, 内容长度: %d", fileId, content.length));
            return new FileContentWrapper(content, fileType, fileId, false);
        } else {
            // 非 Excel 文件：查找对应的文本文件
            String targetFileName = fileId + "_text.txt";
            File textFile = findTextFile(fileId, targetFileName, userId, fileService);
            if (textFile == null) {
                // 尝试实时转换（如果不存在，可能是 prepareFiles 未执行或失败）
                System.out.println("未找到文本文件，尝试实时转换：" + originalFile.getOriginalName());
                byte[] originalContent = fileService.getFileContent(fileId, userId);
                if (originalContent == null) {
                    throw new Exception("读取原始文件内容失败：" + fileId);
                }
                String textContent = ExtractText.convertToText(originalContent, originalFile.getOriginalName());
                MultipartFile txtFile = JsonToExcelWriterUtil.createMultipartFile(
                        targetFileName, targetFileName, "text/plain",
                        textContent.getBytes(StandardCharsets.UTF_8));
                textFile = fileService.saveFile(txtFile, "temp", userId);
                System.out.println("实时转换并保存文本文件：" + targetFileName + " (ID: " + textFile.getId() + ")");
            }
            byte[] content = fileService.getFileContent(textFile.getId(), userId);
            return new FileContentWrapper(content, "TEXT", fileId, true);
        }
    }

    // ==================== AI 决策文件映射 ====================

    /**
     * AI 决策文件映射（保持不变，但 FileSummary 已扩展）
     */
    public static Map<Integer, List<String>> decideFileMapping(
            List<Map<String, Object>> tablesOrSheets,
            List<FileSummary> fileSummaries,
            String userDescription,
            String userId,
            UserConfigService userConfigService,
            ApiService apiService,
            FileService fileService) throws Exception {
        log.info("========== 开始 AI 决策文件映射 ==========");
        if (fileSummaries.isEmpty()) {
            log.warn("没有可用的读取文件，将返回空映射");
            return new HashMap<>();
        }

        UserConfig userConfig = userConfigService.getUserConfig(userId);
        String apiKey = (userConfig != null) ? userConfig.getSiliconFlowApiKey() : null;
        String apiUrl = (userConfig != null && userConfig.getSiliconFlowBaseUrl() != null)
                ? userConfig.getSiliconFlowBaseUrl()
                : "https://api.siliconflow.com/v1";
        String modelName = (userConfig != null && userConfig.getAnalysisModelName() != null)
                ? userConfig.getAnalysisModelName()
                : "deepseek-ai/DeepSeek-V3.2";

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
            System.out.println("AI 决策完成，映射关系：{}" + mapping);
            return mapping;
        } catch (Exception e) {
            log.error("AI 决策失败，降级为关键词匹配：{}", e.getMessage());
            return keywordBasedMapping(tablesOrSheets, fileSummaries);
        }
    }

    private static String callAIWithRetry(String prompt, String apiUrl, String apiKey, String modelName,
                                          ApiService apiService) throws Exception {
        return callAIWithRetry(prompt, apiUrl, apiKey, modelName, apiService, true);
    }
    
    private static String callAIWithRetry(String prompt, String apiUrl, String apiKey, String modelName,
                                          ApiService apiService, boolean enableThinking) throws Exception {
        long waitTime = INITIAL_RETRY_DELAY;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            try {
                String response = apiService.callExternalApi(apiUrl, apiKey, prompt, modelName, enableThinking);
                if (response != null && !response.trim().isEmpty())
                    return response;
            } catch (Exception e) {
                log.warn("AI 调用失败 (尝试 {}/{}): {}", retry + 1, MAX_RETRIES + 1, e.getMessage());
                if (retry == MAX_RETRIES)
                    throw e;
                Thread.sleep(waitTime);
                waitTime *= 2;
            }
        }
        throw new Exception("所有重试均失败");
    }

    private static String buildMappingPrompt(List<Map<String, Object>> tablesOrSheets,
                                             List<FileSummary> fileSummaries,
                                             String userDescription) {
        StringBuilder prompt = new StringBuilder();
        
        // 角色定义和核心任务
        prompt.append("你是一个以满足用户要求为第一目标的智能填表助手。\n\n");
        prompt.append("【核心任务】\n");
        prompt.append("根据用户描述和提供的文件信息，为每个表格/工作表推荐应当使用的读取文件。\n\n");
        
        // 当前输入信息
        prompt.append("【当前输入信息】\n");
        prompt.append("模板中的表格/工作表信息：\n");
        for (int i = 0; i < tablesOrSheets.size(); i++) {
            Map<String, Object> table = tablesOrSheets.get(i);
            prompt.append("表格").append(i).append(":\n");
            prompt.append("  - 表头：").append(String.join(", ", (List<String>) table.get("headers"))).append("\n");
            if (table.containsKey("fileName") && !((String) table.get("fileName")).isEmpty())
                prompt.append("  - 所在文件名：").append(table.get("fileName")).append("\n");
            if (table.containsKey("sheetDiscription1") && !((String) table.get("sheetDiscription1")).isEmpty())
                prompt.append("  - 上文：").append(table.get("sheetDiscription1")).append("\n");
            if (table.containsKey("sheetDiscription2") && !((String) table.get("sheetDiscription2")).isEmpty())
                prompt.append("  - 下文：").append(table.get("sheetDiscription2")).append("\n");
            if (table.containsKey("sheetName"))
                prompt.append("  - 工作表名：").append(table.get("sheetName")).append("\n");
            prompt.append("\n");
        }
        
        prompt.append("可用的读取文件：\n");
        for (int i = 0; i < fileSummaries.size(); i++) {
            FileSummary summary = fileSummaries.get(i);
            prompt.append("文件 ").append(i + 1).append(":\n");
            prompt.append("  - 文件名：").append(summary.getFileName()).append("\n");
            prompt.append("  - 文件 ID: ").append(summary.getFileId()).append("\n");
            prompt.append("  - 类型：").append(summary.getFileType()).append("\n");
            prompt.append("  - 摘要：").append(summary.getSummary()).append("\n");
            prompt.append("\n");
        }
        
        if (userDescription != null && !userDescription.isEmpty()) {
            prompt.append("用户描述：\n");
            prompt.append(userDescription).append("\n\n");
        } else {
            prompt.append("\n");
        }
        
        // 关键约束
        prompt.append("【关键约束】\n");
        prompt.append("1. 必须基于用户描述、文件名、表头列名、文件内容进行匹配。\n");
        prompt.append("2. 用户要求优先于一切：\n");
        prompt.append("   - 若用户要求或隐喻使用某文件，则必须使用该文件（即使内容不相关）。\n");
        prompt.append("   - 若用户要求或隐喻使用特定数量的文件，则必须尽可能满足数量要求（即使不合理），其次再考虑内容合理性。\n");
        prompt.append("3. 只允许返回提供的文件 ID 列表中存在的 ID。\n");
        prompt.append("4. 如果某个表格没有合适的文件，返回空列表 []。\n");
        prompt.append("5. 支持结构化与非结构化数据的匹配。\n\n");
        
        // 输出格式
        prompt.append("【输出格式】\n");
        prompt.append("直接返回 JSON 对象，格式如下：\n");
        prompt.append("{ \"0\": [\"fileId1\", \"fileId2\"], \"1\": [\"fileId3\"], ... }\n");
        prompt.append("- 键为表格序号（字符串形式）\n");
        prompt.append("- 值为文件 ID 列表\n");
        prompt.append("- 不得包含任何额外说明文字，不得使用代码块标记\n");

        System.out.println(prompt.toString());

        return prompt.toString();
    }

    private static Map<Integer, List<String>> parseAIMappingResponse(String aiResponse,
                                                                      List<FileSummary> fileSummaries) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> validFileIds = new HashSet<>();
        for (FileSummary s : fileSummaries)
            validFileIds.add(s.getFileId());

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
                        if (validFileIds.contains(fileId))
                            ids.add(fileId);
                        else
                            log.warn("AI 返回了无效的文件 ID: {}", fileId);
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
        for (FileSummary s : fileSummaries)
            valid.add(s.getFileId());
        Map<Integer, List<String>> cleaned = new HashMap<>();
        for (int i = 0; i < totalTables; i++) {
            List<String> ids = mapping.getOrDefault(i, new ArrayList<>());
            ids.removeIf(id -> !valid.contains(id));
            if (ids.isEmpty()) {
                log.warn("表格 {} 没有匹配到任何文件，将使用所有文件作为备选", i);
                for (FileSummary s : fileSummaries)
                    ids.add(s.getFileId());
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
                    if (h != null && lowerFileName.contains(h.toLowerCase())) {
                        match = true;
                        break;
                    }
                }
                if (!match && s.getSummary() != null) {
                    String lowerSum = s.getSummary().toLowerCase();
                    for (String h : headers) {
                        if (h != null && lowerSum.contains(h.toLowerCase())) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match)
                    matched.add(s.getFileId());
            }
            if (matched.isEmpty()) {
                log.warn("表格 {} 关键词匹配失败，将使用所有文件", i);
                for (FileSummary s : fileSummaries)
                    matched.add(s.getFileId());
            }
            mapping.put(i, matched);
        }
        return mapping;
    }

    private static String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty())
            return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}"))
            return trimmed;
        String markdownCleaned = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}"))
            return trimmed;
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : null;
    }

    // ==================== 文件类型工具 ====================

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

    private static boolean isExcelFile(String fileType) {
        return "Excel".equals(fileType);
    }
}