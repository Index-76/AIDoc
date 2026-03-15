package com.project.aidoc.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 统一的多模板智能填充服务类
 * 整合 Excel 和 Word 的填表逻辑，支持多读取文件、AI 决策文件映射、主键冲突策略等功能
 * 合并自原 TxtToExcel 和 TxtToWord，并整合 TemplateFillService 与 AIFileHelper 的核心流程
 */
public class TxtToTemplate {
    private static final Logger log = LoggerFactory.getLogger(TxtToTemplate.class);

    // 可配置参数（可通过系统属性覆盖）
    private static int SEGMENT_SIZE = Integer.parseInt(System.getProperty("template.segment.size", "30720"));
    private static int OVERLAP_SIZE = Integer.parseInt(System.getProperty("template.overlap.size", "300"));
    private static int MAX_CONCURRENT_AI_CALLS = Integer.parseInt(System.getProperty("template.max.concurrent", "10"));
    private static long AI_EXTRACT_TIMEOUT_MINUTES = Long.parseLong(System.getProperty("ai.extract.timeout.minutes", "10"));
    private static String PRIMARY_KEY_CONFLICT_STRATEGY = System.getProperty("template.pk.conflict.strategy", "KEEP_FIRST");
    private static boolean KEEP_TEXT_FILES = Boolean.parseBoolean(System.getProperty("template.keep.text.files", "true"));

    private static final String MISSING_VALUE_MARKER = "<null>";
    private static final String AI_RESULT_FILE_PREFIX = "_ai_result_";

    // ==================== 内部配置类 ====================
    public static class TemplateFillConfig {
        private int headerRowCount = 0;
        private int headerRowIndex = 0;
        private String primaryKeyHeader;
        private String primaryKeyConflictStrategy;
        private Integer segmentSize;
        private Integer overlapSize;

        // getters and setters
        public int getHeaderRowCount() { return headerRowCount; }
        public void setHeaderRowCount(int headerRowCount) { this.headerRowCount = headerRowCount; }
        public int getHeaderRowIndex() { return headerRowIndex; }
        public void setHeaderRowIndex(int headerRowIndex) { this.headerRowIndex = headerRowIndex; }
        public String getPrimaryKeyHeader() { return primaryKeyHeader; }
        public void setPrimaryKeyHeader(String primaryKeyHeader) { this.primaryKeyHeader = primaryKeyHeader; }
        public String getPrimaryKeyConflictStrategy() { return primaryKeyConflictStrategy; }
        public void setPrimaryKeyConflictStrategy(String primaryKeyConflictStrategy) { this.primaryKeyConflictStrategy = primaryKeyConflictStrategy; }
        public Integer getSegmentSize() { return segmentSize; }
        public void setSegmentSize(Integer segmentSize) { this.segmentSize = segmentSize; }
        public Integer getOverlapSize() { return overlapSize; }
        public void setOverlapSize(Integer overlapSize) { this.overlapSize = overlapSize; }
    }

    // ==================== 内部数据类 ====================
    public static class TextSegment {
        private int segmentId;
        private String content;
        private int startPos;
        private int endPos;
        // getters/setters
        public int getSegmentId() { return segmentId; }
        public void setSegmentId(int segmentId) { this.segmentId = segmentId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getStartPos() { return startPos; }
        public void setStartPos(int startPos) { this.startPos = startPos; }
        public int getEndPos() { return endPos; }
        public void setEndPos(int endPos) { this.endPos = endPos; }
    }

    public static class SegmentResult {
        private int segmentId;
        private int totalRows;
        private String primaryKeyColumn;
        private List<Map<String, Object>> rows; // 每行是列名到值的映射

        // getters/setters
        public int getSegmentId() { return segmentId; }
        public void setSegmentId(int segmentId) { this.segmentId = segmentId; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public String getPrimaryKeyColumn() { return primaryKeyColumn; }
        public void setPrimaryKeyColumn(String primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    }

    /**
     * 带段号的行数据包装类，用于段边界合并处理
     */
    public static class RowWithSegment {
        private Object[] row;
        private int segmentId;

        public RowWithSegment(Object[] row, int segmentId) {
            this.row = row;
            this.segmentId = segmentId;
        }
        public Object[] getRow() { return row; }
        public void setRow(Object[] row) { this.row = row; }
        public int getSegmentId() { return segmentId; }
        public void setSegmentId(int segmentId) { this.segmentId = segmentId; }
    }

    // ==================== 统一入口 ====================
    /**
     * 多模板智能填充入口（无模板配置）
     */
    public static String process(List<File> readFiles, List<File> templateFiles, String userId,
                                  FileService fileService, UserConfigService userConfigService,
                                  ApiService apiService, String userDescription) {
        return process(readFiles, templateFiles, userId, fileService, userConfigService,
                apiService, userDescription, null);
    }

    /**
     * 多模板智能填充入口（支持模板配置）
     */
    public static String process(List<File> readFiles, List<File> templateFiles, String userId,
                                  FileService fileService, UserConfigService userConfigService,
                                  ApiService apiService, String userDescription,
                                  Map<String, TemplateFillConfig> templateConfigs) {
        log.info("========== 开始执行多模板智能填充 ==========");
        if (readFiles.isEmpty()) return "错误：未选择读取文件";
        if (templateFiles.isEmpty()) return "错误：未选择模板文件";

        int successCount = 0, failureCount = 0;
        List<String> failedTemplates = new ArrayList<>();

        try {
            // Step 1: 准备全局文本文件池
            log.info("[Step 1] 准备全局文本文件池...");
            List<AIFileHelper.FileSummary> fileSummaries = AIFileHelper.prepareTextFiles(readFiles, userId, fileService);
            log.info("[Step 1] 文本文件池准备完成，共 {} 个文件", fileSummaries.size());

            // Step 2: 处理每个模板文件
            for (int t = 0; t < templateFiles.size(); t++) {
                File templateFile = templateFiles.get(t);
                log.info("\n========== 开始处理模板 {}/{}: {} (ID: {}) ==========",
                        t + 1, templateFiles.size(), templateFile.getOriginalName(), templateFile.getId());

                boolean isWordTemplate = isWordFile(templateFile.getOriginalName());
                log.info("检测到模板类型: {}", isWordTemplate ? "Word" : "Excel");

                Set<String> tempFileIds = new HashSet<>();
                boolean templateSuccess = false;

                try {
                    TemplateFillConfig config = (templateConfigs != null) ? templateConfigs.get(templateFile.getId()) : null;
                    if (config == null) config = new TemplateFillConfig();

                    String result = processSingleTemplate(templateFile, readFiles, fileSummaries, userId,
                            fileService, userConfigService, apiService, isWordTemplate,
                            tempFileIds, userDescription, config);

                    if (result != null && result.contains("成功")) {
                        successCount++;
                        templateSuccess = true;
                        log.info("模板 {} 处理成功", templateFile.getOriginalName());
                    } else {
                        failureCount++;
                        failedTemplates.add(templateFile.getOriginalName() + ": " + result);
                        log.warn("模板 {} 处理失败: {}", templateFile.getOriginalName(), result);
                    }
                } catch (Exception e) {
                    failureCount++;
                    failedTemplates.add(templateFile.getOriginalName() + ": " + e.getMessage());
                    log.error("模板 {} 处理异常", templateFile.getOriginalName(), e);
                } finally {
                    if (!templateSuccess) {
                        log.info("检测到模板 {} 处理失败，开始清理临时文件（共 {} 个）",
                                templateFile.getOriginalName(), tempFileIds.size());
                        for (String fileId : tempFileIds) {
                            try { fileService.deleteFileById(fileId, userId); }
                            catch (Exception ex) { log.warn("删除临时文件失败：{}", fileId, ex); }
                        }
                    }
                }
            }

            log.info("\n========== 多模板智能填充完成 ==========");
            log.info("成功: {}/{}", successCount, templateFiles.size());
            log.info("失败: {}/{}", failureCount, templateFiles.size());

            if (failureCount > 0) {
                return String.format("完成 %d/%d 个模板，失败的模板：%s",
                        successCount, templateFiles.size(), String.join(", ", failedTemplates));
            } else {
                return "所有模板已智能填充完成";
            }
        } catch (Exception e) {
            log.error("========== 多模板智能填充异常 ==========", e);
            return "智能填充失败：" + e.getMessage();
        }
    }

    private static boolean isWordFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }

    // ==================== 单个模板处理 ====================
    private static String processSingleTemplate(
            File templateFile, List<File> readFiles, List<AIFileHelper.FileSummary> fileSummaries,
            String userId, FileService fileService, UserConfigService userConfigService,
            ApiService apiService, boolean isWordTemplate, Set<String> tempFileIds,
            String userDescription, TemplateFillConfig config) throws Exception {

        // 2.1 提取表头信息
        log.info("[Step 2.1] 开始提取模板表头信息...");
        Map<String, Object> headerInfo = extractHeaderInfo(templateFile, userId, fileService, isWordTemplate, config);
        if (headerInfo == null) return "提取模板头信息失败";
        String headerFileId = (String) headerInfo.get("fileId");
        tempFileIds.add(headerFileId);

        // 2.2 AI 决策文件映射
        log.info("[Step 2.2] 开始 AI 决策文件映射...");
        List<Map<String, Object>> tablesOrSheets = isWordTemplate
                ? (List<Map<String, Object>>) headerInfo.get("tables")
                : (List<Map<String, Object>>) headerInfo.get("sheets");

        Map<Integer, List<String>> fileMapping = AIFileHelper.decideFileMapping(
                tablesOrSheets, fileSummaries, userDescription, userId, userConfigService, apiService);
        String mappingFileId = saveFileMapping(fileMapping, templateFile.getId(), userId, fileService);
        tempFileIds.add(mappingFileId);

        // 2.3 创建模板副本
        log.info("[Step 2.3] 开始创建模板副本...");
        String filledFileId = createTemplateCopy(templateFile, userId, fileService, isWordTemplate);
        if (filledFileId == null) return "创建模板副本失败";
        tempFileIds.add(filledFileId);

        // 2.4 按表格/工作表并行处理
        log.info("[Step 2.4] 开始并行处理各表格...");
        String conflictStrategy = config.getPrimaryKeyConflictStrategy();
        if (conflictStrategy == null) {
            conflictStrategy = PRIMARY_KEY_CONFLICT_STRATEGY;
        } else {
            if (!"KEEP_FIRST".equalsIgnoreCase(conflictStrategy) &&
                !"KEEP_LAST".equalsIgnoreCase(conflictStrategy) &&
                !"MERGE".equalsIgnoreCase(conflictStrategy)) {
                log.warn("无效的主键冲突策略 '{}'，使用默认 KEEP_FIRST", conflictStrategy);
                conflictStrategy = "KEEP_FIRST";
            }
        }

        Map<Integer, List<Object[]>> mergedTableData = processTablesParallel(
                tablesOrSheets, fileMapping, fileSummaries, userId,
                fileService, userConfigService, apiService,
                templateFile.getId(), tempFileIds, isWordTemplate, conflictStrategy);

        // 2.5 填充模板
        log.info("[Step 2.5] 开始填充模板...");
        String updatedFileId = fillTemplate(filledFileId, mergedTableData, headerInfo,
                userId, fileService, isWordTemplate, templateFile.getOriginalName());
        tempFileIds.remove(filledFileId);
        tempFileIds.add(updatedFileId);
        filledFileId = updatedFileId;

        // 2.6 清理临时文件并移动结果
        log.info("[Step 2.6] 开始清理临时文件并移动结果...");
        cleanupAndMoveResult(updatedFileId, tempFileIds, userId, fileService, KEEP_TEXT_FILES);

        return "成功";
    }

    private static Map<String, Object> extractHeaderInfo(File templateFile, String userId,
                                                          FileService fileService, boolean isWordTemplate,
                                                          TemplateFillConfig config) throws Exception {
        if (isWordTemplate) {
            return WordTableExtractorUtil.extractTables(templateFile, userId, fileService,
                    config.getHeaderRowCount(), config.getPrimaryKeyHeader());
        } else {
            int headerRowCount = config.getHeaderRowCount();
            if (headerRowCount <= 0) headerRowCount = 1;
            return HeaderExtractorUtil.extractTemplateHeaders(templateFile, userId, fileService,
                    config.getHeaderRowIndex(), headerRowCount, config.getPrimaryKeyHeader());
        }
    }

    private static String saveFileMapping(Map<Integer, List<String>> mapping, String templateFileId,
                                          String userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(mapping);
        MultipartFile file = JsonToExcelWriterUtil.createMultipartFile(
                templateFileId + "_file_mapping.json",
                templateFileId + "_file_mapping.json",
                "application/json",
                json.getBytes(StandardCharsets.UTF_8));
        return fileService.saveFile(file, "temp", userId).getId();
    }

    private static String createTemplateCopy(File templateFile, String userId,
                                              FileService fileService, boolean isWordTemplate) throws Exception {
        byte[] content = fileService.getFileContent(templateFile.getId(), userId);
        if (content == null) return null;
        String extension = isWordTemplate ? ".docx" : ".xlsx";
        String contentType = isWordTemplate
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String fileName = templateFile.getId() + "_filled" + extension;
        MultipartFile copy = JsonToExcelWriterUtil.createMultipartFile(fileName, fileName, contentType, content);
        return fileService.saveFile(copy, "temp", userId).getId();
    }

    // ==================== 表格并行处理 ====================
    private static Map<Integer, List<Object[]>> processTablesParallel(
            List<Map<String, Object>> tablesOrSheets, Map<Integer, List<String>> fileMapping,
            List<AIFileHelper.FileSummary> fileSummaries, String userId,
            FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String templateFileId, Set<String> tempFileIds, boolean isWordTemplate, String conflictStrategy) throws Exception {

        Map<Integer, List<Object[]>> mergedData = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_AI_CALLS);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_AI_CALLS);
        CountDownLatch latch = new CountDownLatch(tablesOrSheets.size());

        try {
            for (int idx = 0; idx < tablesOrSheets.size(); idx++) {
                final int tableIdx = idx;
                executor.submit(() -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;
                        List<Object[]> tableData = processSingleTable(
                                tableIdx, tablesOrSheets.get(tableIdx), fileMapping.get(tableIdx),
                                fileSummaries, userId, fileService, userConfigService, apiService,
                                templateFileId, tempFileIds, isWordTemplate, conflictStrategy);
                        mergedData.put(tableIdx, tableData);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("表格 {} 处理被中断", tableIdx, e);
                    } catch (Exception e) {
                        log.error("表格 {} 处理失败", tableIdx, e);
                    } finally {
                        if (acquired) semaphore.release();
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(AI_EXTRACT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new TimeoutException("表格数据处理超时");
            }
        } finally {
            executor.shutdownNow();
        }
        return mergedData;
    }

    private static List<Object[]> processSingleTable(
            int tableIndex, Map<String, Object> tableInfo, List<String> fileIds,
            List<AIFileHelper.FileSummary> fileSummaries, String userId,
            FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String templateFileId, Set<String> tempFileIds, boolean isWordTemplate, String conflictStrategy) throws Exception {

        if (fileIds == null || fileIds.isEmpty()) return new ArrayList<>();

        String mergedText = mergeFileTexts(fileIds, userId, fileService);
        List<TextSegment> segments = segmentText(mergedText);
        log.info("表格 {}: 文本切割为 {} 个段落", tableIndex, segments.size());

        // 并行 AI 提取，返回合并后的段落结果列表
        List<SegmentResult> segmentResults = extractDataWithAI(
                segments, tableInfo, userId, fileService, userConfigService, apiService,
                templateFileId, tableIndex, tempFileIds, isWordTemplate);

        // 合并段落数据
        List<Object[]> tableRows = mergeSegmentData(segmentResults, tableInfo, userId, fileService, conflictStrategy);
        log.info("表格 {}: 数据合并完成，共 {} 行", tableIndex, tableRows.size());
        return tableRows;
    }

    private static String mergeFileTexts(List<String> fileIds, String userId, FileService fileService) throws Exception {
        StringBuilder sb = new StringBuilder();
        List<String> sorted = new ArrayList<>(fileIds);
        Collections.sort(sorted);
        for (String fid : sorted) {
            sb.append(AIFileHelper.getTextFileContent(fid, userId, fileService));
            sb.append("\n\n--- 文件分隔符 ---\n\n");
        }
        return sb.toString();
    }

    private static List<TextSegment> segmentText(String fullText) {
        List<TextSegment> segments = new ArrayList<>();
        int nextStart = 0, segIndex = 0;
        while (nextStart < fullText.length()) {
            int segStart = nextStart;
            int segEnd = Math.min(nextStart + SEGMENT_SIZE, fullText.length());
            if (segIndex > 0 && segStart > 0) segStart = Math.max(0, segStart - OVERLAP_SIZE);
            if (segEnd < fullText.length()) {
                int lastNewline = fullText.lastIndexOf('\n', segEnd);
                if (lastNewline > segStart) segEnd = lastNewline + 1;
            }
            TextSegment seg = new TextSegment();
            seg.setSegmentId(segIndex);
            seg.setContent(fullText.substring(segStart, segEnd));
            seg.setStartPos(segStart);
            seg.setEndPos(segEnd);
            segments.add(seg);
            nextStart = segEnd;
            segIndex++;
        }
        return segments;
    }

    private static List<SegmentResult> extractDataWithAI(
            List<TextSegment> segments, Map<String, Object> tableInfo, String userId,
            FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String templateFileId, int tableIndex, Set<String> tempFileIds, boolean isWordTemplate) throws Exception {

        List<SegmentResult> results = new ArrayList<>(segments.size());
        CountDownLatch latch = new CountDownLatch(segments.size());
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_AI_CALLS);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_AI_CALLS);
        ConcurrentLinkedQueue<SegmentResult> queue = new ConcurrentLinkedQueue<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicReference<Exception> failureException = new AtomicReference<>();

        try {
            for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
                final int idx = segIdx;
                executor.submit(() -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;
                        SegmentResult result = callAIForSegment(segments.get(idx), tableInfo,
                                userId, userConfigService, apiService, isWordTemplate);
                        if (result != null && result.getRows() != null && !result.getRows().isEmpty()) {
                            queue.add(result);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        hasFailure.set(true);
                        failureException.set(e);
                        log.error("段落 {} AI 提取被中断", idx, e);
                    } catch (Exception e) {
                        hasFailure.set(true);
                        failureException.set(e);
                        log.error("段落 {} AI 提取失败", idx, e);
                    } finally {
                        if (acquired) semaphore.release();
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(AI_EXTRACT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new TimeoutException("AI 数据提取超时");
            }
            if (hasFailure.get() && queue.isEmpty()) {
                throw new Exception("所有 AI 提取任务均失败：" +
                        (failureException.get() != null ? failureException.get().getMessage() : "未知错误"));
            }
            results.addAll(queue);
            results.sort(Comparator.comparingInt(SegmentResult::getSegmentId));

            // 将结果保存为一个文件
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(results);
            String fileName = templateFileId + AI_RESULT_FILE_PREFIX + "table" + tableIndex + ".json";
            MultipartFile resultFile = JsonToExcelWriterUtil.createMultipartFile(
                    fileName, fileName, "application/json", json.getBytes(StandardCharsets.UTF_8));
            File saved = fileService.saveFile(resultFile, "temp", userId);
            tempFileIds.add(saved.getId());
            log.info("表格 {} {} 个段落结果已合并保存到文件: {}", tableIndex, results.size(), saved.getId());

        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    private static SegmentResult callAIForSegment(TextSegment segment, Map<String, Object> tableInfo,
                                                   String userId, UserConfigService userConfigService,
                                                   ApiService apiService, boolean isWordTemplate) throws Exception {
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        String apiKey = (userConfig != null) ? userConfig.getSiliconFlowApiKey() : null;
        String apiUrl = (userConfig != null && userConfig.getSiliconFlowBaseUrl() != null)
                ? userConfig.getSiliconFlowBaseUrl() : "https://api.siliconflow.com/v1";
        String modelName = (userConfig != null && userConfig.getAnalysisModelName() != null)
                ? userConfig.getAnalysisModelName() : "deepseek-ai/DeepSeek-V3.2";
        if (apiKey == null || apiKey.isEmpty()) throw new Exception("未配置 API Key");

        StringBuilder prompt = new StringBuilder();
        prompt.append("请从以下文本中提取表格数据。\n\n");
        List<String> headers = (List<String>) tableInfo.get("headers");
        prompt.append("表头为：").append(String.join(", ", headers)).append("\n\n");
        if (isWordTemplate) {
            if (tableInfo.containsKey("sheetDiscription1") && !((String) tableInfo.get("sheetDiscription1")).isEmpty())
                prompt.append("上文背景：").append(tableInfo.get("sheetDiscription1")).append("\n");
            if (tableInfo.containsKey("sheetDiscription2") && !((String) tableInfo.get("sheetDiscription2")).isEmpty())
                prompt.append("下文背景：").append(tableInfo.get("sheetDiscription2")).append("\n");
        }
        prompt.append("文本内容编号：").append(segment.getSegmentId()).append("\n");
        prompt.append("文本内容:\n").append(segment.getContent()).append("\n\n");
        prompt.append("请严格按照以下 JSON 格式返回结果:\n");
        prompt.append("{\n");
        prompt.append("  \"segmentId\": ").append(segment.getSegmentId()).append(",\n");
        prompt.append("  \"primaryKeyColumn\": \"主键列名（如果存在）\",\n");
        prompt.append("  \"rows\": [\n");
        prompt.append("    {\n");
        for (int i = 0; i < headers.size(); i++) {
            prompt.append("      \"").append(headers.get(i)).append("\": \"单元格内容\"");
            if (i < headers.size() - 1) prompt.append(",");
            prompt.append("\n");
        }
        prompt.append("    },\n");
        prompt.append("    ...\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("重要要求:\n");
        prompt.append("1. 必须返回纯 JSON 数据，不要任何额外文字\n");
        prompt.append("2. 禁止使用 Markdown 代码块\n");
        prompt.append("3. rows 数组中的每个对象键为表头列名，值为对应单元格内容\n");
        prompt.append("4. 若单元格无数据，请使用 null\n");
        prompt.append("5. 主键列名应指明哪一列作为主键（若有）\n");
        prompt.append("6. 确保 JSON 格式完整可解析\n");
        prompt.append("7. 单元格中尽量不添加数据的单位\n");
        prompt.append("8. 确保尽可能找齐表头所对应的单元格数据\n");

        String aiResponse = callAIWithRetry(prompt.toString(), apiUrl, apiKey, modelName, apiService, segment.getSegmentId());
        if (aiResponse == null || aiResponse.trim().isEmpty())
            throw new Exception("AI service returned empty response - Segment " + segment.getSegmentId());

        return parseAIResponse(aiResponse, segment.getSegmentId());
    }

    private static String callAIWithRetry(String prompt, String apiUrl, String apiKey,
                                           String modelName, ApiService apiService, int segId) throws Exception {
        int maxRetries = 2;
        long wait = 1000;
        for (int r = 0; r <= maxRetries; r++) {
            try {
                String resp = apiService.callExternalApi(apiUrl, apiKey, prompt, modelName);
                if (resp != null && !resp.trim().isEmpty()) return resp;
            } catch (Exception e) {
                log.warn("段落 {} AI 调用失败 (尝试 {}/{}): {}", segId, r + 1, maxRetries + 1, e.getMessage());
                if (r == maxRetries) throw e;
                Thread.sleep(wait);
                wait *= 2;
            }
        }
        throw new Exception("所有重试均失败");
    }

    private static SegmentResult parseAIResponse(String aiResponse, int segmentId) throws Exception {
        SegmentResult result = new SegmentResult();
        result.setSegmentId(segmentId);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String json = extractJsonFromResponse(aiResponse);
        if (json == null) throw new Exception("无法从 AI 响应中提取有效 JSON");

        JsonNode root = mapper.readTree(json);
        if (root.has("segmentId")) result.setSegmentId(root.get("segmentId").asInt(segmentId));
        if (root.has("primaryKeyColumn")) {
            result.setPrimaryKeyColumn(root.get("primaryKeyColumn").asText());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode rowsNode = root.get("rows");
        if (rowsNode != null && rowsNode.isArray()) {
            for (JsonNode rowNode : rowsNode) {
                Map<String, Object> rowMap = mapper.convertValue(rowNode, new TypeReference<Map<String, Object>>() {});
                rows.add(rowMap);
            }
        }
        result.setRows(rows);
        result.setTotalRows(rows.size());
        log.info("解析 AI 响应 - 段落{}, 共 {} 行", segmentId, rows.size());
        return result;
    }

    private static String extractJsonFromResponse(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        String cleaned = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) return cleaned;
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : null;
    }

    private static List<Object[]> mergeSegmentData(List<SegmentResult> segmentResults,
                                                    Map<String, Object> tableInfo, String userId,
                                                    FileService fileService, String conflictStrategy) throws Exception {
        List<String> headers = (List<String>) tableInfo.get("headers");
        int colCount = headers.size();
        String userPrimaryKeyHeader = (String) tableInfo.get("primaryKeyHeader");

        // 收集 AI 返回的主键列名及其出现次数
        Map<String, Integer> aiPrimaryKeyCounts = new HashMap<>();
        for (SegmentResult seg : segmentResults) {
            if (seg.getPrimaryKeyColumn() != null && !seg.getPrimaryKeyColumn().isEmpty()) {
                String pk = seg.getPrimaryKeyColumn();
                aiPrimaryKeyCounts.put(pk, aiPrimaryKeyCounts.getOrDefault(pk, 0) + 1);
            }
        }

        // 确定最终主键列名
        String finalPrimaryKey = userPrimaryKeyHeader;
        if (finalPrimaryKey == null || finalPrimaryKey.isEmpty()) {
            if (aiPrimaryKeyCounts.isEmpty()) {
                log.info("AI 未返回主键列名，将使用无主键模式");
                finalPrimaryKey = null;
            } else {
                String mostFrequentPk = null;
                int maxCount = 0;
                for (Map.Entry<String, Integer> entry : aiPrimaryKeyCounts.entrySet()) {
                    if (entry.getValue() > maxCount) {
                        maxCount = entry.getValue();
                        mostFrequentPk = entry.getKey();
                    }
                }
                // 检查是否有并列第一
                int finalMaxCount = maxCount;
                List<String> mostFrequentList = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : aiPrimaryKeyCounts.entrySet()) {
                    if (entry.getValue() == finalMaxCount) mostFrequentList.add(entry.getKey());
                }
                if (mostFrequentList.size() > 1) {
                    log.warn("AI 返回了多个出现次数相同的主键列名：{}，将选择第一个：{}", mostFrequentList, mostFrequentList.get(0));
                }
                finalPrimaryKey = mostFrequentPk;
                log.info("根据出现次数选择主键列名：{}（出现 {} 次）", finalPrimaryKey, maxCount);
            }
        } else {
            log.info("使用用户配置的主键列名：{}", finalPrimaryKey);
        }

        // 确定主键列索引
        Integer pkColIndex = null;
        if (finalPrimaryKey != null) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equals(finalPrimaryKey)) {
                    pkColIndex = i;
                    break;
                }
            }
            if (pkColIndex == null) {
                log.warn("主键列名 {} 不在表头列表中，降级为无主键模式", finalPrimaryKey);
            }
        }

        // 将所有段的行数据转换为 Object[] 列表（带段号）
        List<RowWithSegment> allRowsWithSegment = new ArrayList<>();
        for (SegmentResult seg : segmentResults) {
            List<Map<String, Object>> rows = seg.getRows();
            if (rows == null) continue;
            for (Map<String, Object> rowMap : rows) {
                Object[] rowArray = new Object[colCount];
                for (int i = 0; i < colCount; i++) {
                    rowArray[i] = rowMap.get(headers.get(i));
                }
                allRowsWithSegment.add(new RowWithSegment(rowArray, seg.getSegmentId()));
            }
        }

        // 过滤全空行
        allRowsWithSegment.removeIf(r -> isAllEmpty(r.row));

        // 使用段边界合并算法
        return mergeAllRowsWithSegmentBoundary(allRowsWithSegment, pkColIndex, colCount, conflictStrategy);
    }

    private static boolean isAllEmpty(Object[] row) {
        for (Object o : row) {
            if (o != null) {
                String s = o.toString().trim();
                if (!s.isEmpty()) return false;
            }
        }
        return true;
    }

    // ==================== 段边界合并算法 ====================
    private static List<Object[]> mergeAllRowsWithSegmentBoundary(
            List<RowWithSegment> allRowsWithSegment,
            Integer pkColIndex,
            int colCount,
            String conflictStrategy) {

        if (allRowsWithSegment.isEmpty()) return new ArrayList<>();

        // 按段号分组
        Map<Integer, List<RowWithSegment>> segmentMap = new LinkedHashMap<>();
        for (RowWithSegment r : allRowsWithSegment) {
            segmentMap.computeIfAbsent(r.segmentId, k -> new ArrayList<>()).add(r);
        }
        List<Integer> segmentIds = new ArrayList<>(segmentMap.keySet());
        Collections.sort(segmentIds);

        List<Object[]> processedRows = new ArrayList<>();

        for (int segId : segmentIds) {
            List<RowWithSegment> rowsInSegment = segmentMap.get(segId);
            // 段内去重（仅去除完全相同的行）
            List<RowWithSegment> uniqueInSegment = new ArrayList<>();
            for (RowWithSegment r : rowsInSegment) {
                boolean dup = false;
                for (RowWithSegment ex : uniqueInSegment) {
                    if (arraysEqualAsStrings(r.row, ex.row)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) uniqueInSegment.add(r);
            }
            if (uniqueInSegment.isEmpty()) continue;

            if (processedRows.isEmpty()) {
                // 第一段：直接添加所有行
                for (RowWithSegment r : uniqueInSegment) {
                    processedRows.add(r.row);
                }
            } else {
                // 尝试匹配上一段最后一行与当前段第一行
                Object[] prevLastRow = processedRows.get(processedRows.size() - 1);
                RowWithSegment prevLast = new RowWithSegment(prevLastRow, -1);
                RowWithSegment currFirst = uniqueInSegment.get(0);

                boolean needMerge = false;
                RowWithSegment merged = null;

                if (pkColIndex != null) {
                    Object prevPk = prevLast.row[pkColIndex];
                    Object currPk = currFirst.row[pkColIndex];
                    boolean pkEqual = (prevPk != null && currPk != null && prevPk.toString().equals(currPk.toString()));

                    if (pkEqual) {
                        needMerge = true;
                    } else {
                        RowWithSegment mergedTemp = mergeTwoRows(prevLast, currFirst, pkColIndex, colCount, "MERGE");
                        boolean sameAsPrev = arraysEqualAsStrings(mergedTemp.row, prevLast.row);
                        boolean sameAsCurr = arraysEqualAsStrings(mergedTemp.row, currFirst.row);
                        if (!sameAsPrev && !sameAsCurr) {
                            needMerge = true;
                            merged = mergedTemp;
                        }
                    }
                } else {
                    // 无主键模式
                    RowWithSegment mergedTemp = mergeTwoRows(prevLast, currFirst, null, colCount, "MERGE");
                    boolean sameAsPrev = arraysEqualAsStrings(mergedTemp.row, prevLast.row);
                    boolean sameAsCurr = arraysEqualAsStrings(mergedTemp.row, currFirst.row);
                    if (!sameAsPrev && !sameAsCurr) {
                        needMerge = true;
                        merged = mergedTemp;
                    }
                }

                if (needMerge) {
                    if (merged == null) {
                        merged = mergeTwoRows(prevLast, currFirst, pkColIndex, colCount, conflictStrategy);
                    }
                    processedRows.set(processedRows.size() - 1, merged.row);
                    for (int i = 1; i < uniqueInSegment.size(); i++) {
                        processedRows.add(uniqueInSegment.get(i).row);
                    }
                } else {
                    for (RowWithSegment r : uniqueInSegment) {
                        processedRows.add(r.row);
                    }
                }
            }
        }
        return processedRows;
    }

    private static RowWithSegment mergeTwoRows(
            RowWithSegment row1,
            RowWithSegment row2,
            Integer pkColIndex,
            int colCount,
            String conflictStrategy) {

        Object[] mergedRow = new Object[colCount];

        if ("MERGE".equalsIgnoreCase(conflictStrategy)) {
            Arrays.fill(mergedRow, null);
            for (int i = 0; i < colCount; i++) {
                if (row1.row[i] != null) {
                    String valStr = row1.row[i].toString().trim();
                    if (!valStr.isEmpty()) mergedRow[i] = row1.row[i];
                }
            }
            for (int i = 0; i < colCount; i++) {
                if (mergedRow[i] == null && row2.row[i] != null) {
                    String valStr = row2.row[i].toString().trim();
                    if (!valStr.isEmpty()) mergedRow[i] = row2.row[i];
                }
            }
            boolean allNull = true;
            for (int i = 0; i < colCount; i++) {
                if (mergedRow[i] != null) { allNull = false; break; }
            }
            if (allNull) mergedRow = row1.row;
        } else if ("KEEP_LAST".equalsIgnoreCase(conflictStrategy)) {
            mergedRow = Arrays.copyOf(row2.row, colCount);
            if (pkColIndex != null) {
                Object pk1 = row1.row[pkColIndex];
                Object pk2 = row2.row[pkColIndex];
                if (pk2 == null && pk1 != null) mergedRow[pkColIndex] = pk1;
            }
        } else { // 默认 KEEP_FIRST
            mergedRow = Arrays.copyOf(row1.row, colCount);
            if (pkColIndex != null) {
                Object pk1 = row1.row[pkColIndex];
                Object pk2 = row2.row[pkColIndex];
                if (pk1 == null && pk2 != null) mergedRow[pkColIndex] = pk2;
            }
        }
        return new RowWithSegment(mergedRow, Math.min(row1.segmentId, row2.segmentId));
    }

    private static boolean arraysEqualAsStrings(Object[] a, Object[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            String sa = a[i] != null ? a[i].toString() : "";
            String sb = b[i] != null ? b[i].toString() : "";
            if (!sa.equals(sb)) return false;
        }
        return true;
    }

    // ==================== 填充模板 ====================
    private static String fillTemplate(String filledFileId, Map<Integer, List<Object[]>> mergedTableData,
                                        Map<String, Object> headerInfo, String userId, FileService fileService,
                                        boolean isWordTemplate, String templateOriginalName) throws Exception {
        if (isWordTemplate) {
            return WordSplicerUtil.fillWordTemplate(filledFileId, mergedTableData, headerInfo,
                    userId, fileService, templateOriginalName);
        } else {
            Map<String, List<Object[]>> mergedSheetData = new LinkedHashMap<>();
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");
            for (int i = 0; i < sheets.size(); i++) {
                String sheetName = (String) sheets.get(i).get("sheetName");
                mergedSheetData.put(sheetName, mergedTableData.getOrDefault(i, new ArrayList<>()));
            }
            return JsonToExcelWriterUtil.fillExcelTemplate(filledFileId, mergedSheetData, headerInfo, userId, fileService);
        }
    }

    // ==================== 清理与移动 ====================
    private static void cleanupAndMoveResult(String resultFileId, Set<String> tempFileIds,
                                              String userId, FileService fileService, boolean keepTextFiles) throws Exception {
        File resultFile = fileService.getFileById(resultFileId, userId);
        if (resultFile != null) {
            fileService.moveFileToSection(resultFileId, "result", userId);
            log.info("结果文件已移动到 result 区域");
        }

        for (String fileId : tempFileIds) {
            if (fileId.equals(resultFileId)) continue;
            try {
                File f = fileService.getFileById(fileId, userId);
                if (f == null) continue;
                if (keepTextFiles && f.getFileName() != null && f.getFileName().endsWith("_text.txt")) {
                    log.debug("保留文本文件: {}", f.getFileName());
                    continue;
                }
                fileService.deleteFileById(fileId, userId);
                log.debug("已删除临时文件: {}", fileId);
            } catch (Exception e) {
                log.warn("处理临时文件失败：{}", fileId, e);
            }
        }
        log.info("临时文件清理完成，AI 结果文件和文本文件已保留在 result 区");
    }
}