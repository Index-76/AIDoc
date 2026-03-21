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
 * 多模板智能填充处理器（支持混合文件源：文本文件 + Excel 文件）
 */
public class MultiFileToMultiTemplateProcessor {
    private static final Logger log = LoggerFactory.getLogger(MultiFileToMultiTemplateProcessor.class);

    // 可配置参数（可通过系统属性覆盖）
    private static int SEGMENT_SIZE = Integer.parseInt(System.getProperty("template.segment.size", "18720"));
    private static int MAX_SEGMENTS = Integer.parseInt(System.getProperty("template.max.segments", "10"));
    private static int OVERLAP_SIZE = Integer.parseInt(System.getProperty("template.overlap.size", "300"));
    private static int MAX_CONCURRENT_AI_CALLS = Integer.parseInt(System.getProperty("template.max.concurrent", "10"));
    private static long AI_EXTRACT_TIMEOUT_MINUTES = Long.parseLong(System.getProperty("ai.extract.timeout.minutes", "20"));
    private static String PRIMARY_KEY_CONFLICT_STRATEGY = System.getProperty("template.pk.conflict.strategy", "MERGE");
    private static boolean KEEP_TEXT_FILES = Boolean.parseBoolean(System.getProperty("template.keep.text.files", "true"));
    private static boolean KEEP_EXCEL_SUMMARY = Boolean.parseBoolean(System.getProperty("template.keep.excel.summary", "true"));
    private static int EXCEL_SAMPLE_ROWS = Integer.parseInt(System.getProperty("excel.sample.rows", "5"));

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

    /**
     * 单个文件的行数据封装（用于合并）
     */
    private static class FileRows {
        private List<RowWithSegment> rowsWithSegment; // 文本文件分段后的行（带段号）
        private List<Object[]> flatRows;               // 非文本文件（如 Excel）的行（不带段号）
        private boolean isTextFile;                     // 是否为文本文件

        // 私有构造方法，禁止外部直接 new
        private FileRows() {}

        /**
         * 静态工厂方法：创建带段号的行数据实例
         */
        public static FileRows forRowsWithSegment(List<RowWithSegment> rowsWithSegment, boolean isTextFile) {
            FileRows fr = new FileRows();
            fr.rowsWithSegment = rowsWithSegment;
            fr.flatRows = null;
            fr.isTextFile = isTextFile;
            return fr;
        }

        /**
         * 静态工厂方法：创建扁平行数据实例
         */
        public static FileRows forFlatRows(List<Object[]> flatRows, boolean isTextFile) {
            FileRows fr = new FileRows();
            fr.flatRows = flatRows;
            fr.rowsWithSegment = null;
            fr.isTextFile = isTextFile;
            return fr;
        }

        public List<RowWithSegment> getRowsWithSegment() { return rowsWithSegment; }
        public List<Object[]> getFlatRows() { return flatRows; }
        public boolean isTextFile() { return isTextFile; }
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
        return process(readFiles, templateFiles, userId, fileService, userConfigService,
                apiService, userDescription, templateConfigs, false);
    }

    /**
     * 多模板智能填充入口（支持缓存检查）
     * @param readFiles 读取区文件列表
     * @param templateFiles 模板文件列表
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param userConfigService 用户配置服务
     * @param apiService API 服务
     * @param userDescription 用户描述
     * @param templateConfigs 模板配置
     * @param checkCache 是否检查预处理缓存（若为 true，则优先使用 temp 区已有的预处理文件）
     * @return 处理结果
     */
    public static String process(List<File> readFiles, List<File> templateFiles, String userId,
                                 FileService fileService, UserConfigService userConfigService,
                                 ApiService apiService, String userDescription,
                                 Map<String, TemplateFillConfig> templateConfigs, boolean checkCache) {
        log.info("========== 开始执行多模板智能填充 ==========");
        if (readFiles.isEmpty()) return "错误：未选择读取文件";
        if (templateFiles.isEmpty()) return "错误：未选择模板文件";

        int successCount = 0, failureCount = 0;
        List<String> failedTemplates = new ArrayList<>();

        try {
            // Step 1: 准备全局文件池（使用 AISwitchFilesForFormFiller，支持缓存检查）
            log.info("[Step 1] 准备全局文件池...（缓存检查：{}）", checkCache ? "启用" : "禁用");
            List<AISwitchFilesForFormFiller.FileSummary> fileSummaries =
                    AISwitchFilesForFormFiller.prepareFiles(readFiles, userId, fileService, checkCache);
            log.info("[Step 1] 文件池准备完成，共 {} 个文件", fileSummaries.size());

            // Step 2: 处理每个模板文件
            for (int t = 0; t < templateFiles.size(); t++) {
                File templateFile = templateFiles.get(t);
                log.info("\n========== 开始处理模板 {}/{}: {} (ID: {}) ==========",
                        t + 1, templateFiles.size(), templateFile.getOriginalName(), templateFile.getId());

                boolean isWordTemplate = isWordFile(templateFile.getOriginalName());
                log.info("检测到模板类型：{}", isWordTemplate ? "Word" : "Excel");

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
                        log.warn("模板 {} 处理失败：{}", templateFile.getOriginalName(), result);
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
                            try {
                                fileService.deleteFileById(fileId, userId);
                            } catch (Exception ex) {
                                log.warn("删除临时文件失败：{}", fileId, ex);
                            }
                        }
                    }
                }
            }

            log.info("\n========== 多模板智能填充完成 ==========");
            log.info("成功：{}/{}", successCount, templateFiles.size());
            log.info("失败：{}/{}", failureCount, templateFiles.size());

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

    /**
     * 多模板智能填充入口（直接使用已预处理的文件摘要，跳过预处理步骤）
     * @param readFiles 读取区文件列表
     * @param templateFiles 模板文件列表
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param userConfigService 用户配置服务
     * @param apiService API 服务
     * @param userDescription 用户描述
     * @param templateConfigs 模板配置
     * @param preprocessedSummaries 已预处理的文件摘要列表（由外部缓存提供）
     * @return 处理结果
     */
    public static String processWithPreprocessedFiles(
            List<File> readFiles, List<File> templateFiles, String userId,
            FileService fileService, UserConfigService userConfigService,
            ApiService apiService, String userDescription,
            Map<String, TemplateFillConfig> templateConfigs,
            List<AISwitchFilesForFormFiller.FileSummary> preprocessedSummaries) {
        
        log.info("========== 开始执行多模板智能填充（使用预处理器缓存） ==========");
        if (readFiles.isEmpty()) return "错误：未选择读取文件";
        if (templateFiles.isEmpty()) return "错误：未选择模板文件";
        if (preprocessedSummaries == null || preprocessedSummaries.isEmpty()) {
            return "错误：未提供预处理的文件摘要";
        }

        int successCount = 0, failureCount = 0;
        List<String> failedTemplates = new ArrayList<>();

        try {
            // Step 1: 直接使用已预处理的文件摘要
            log.info("[Step 1] 使用外部提供的预处理文件摘要，共 {} 个文件", preprocessedSummaries.size());

            // Step 2: 处理每个模板文件
            for (int t = 0; t < templateFiles.size(); t++) {
                File templateFile = templateFiles.get(t);
                log.info("\n========== 开始处理模板 {}/{}: {} (ID: {}) ==========",
                        t + 1, templateFiles.size(), templateFile.getOriginalName(), templateFile.getId());

                boolean isWordTemplate = isWordFile(templateFile.getOriginalName());
                log.info("检测到模板类型：{}", isWordTemplate ? "Word" : "Excel");

                Set<String> tempFileIds = new HashSet<>();
                boolean templateSuccess = false;

                try {
                    TemplateFillConfig config = (templateConfigs != null) ? templateConfigs.get(templateFile.getId()) : null;
                    if (config == null) config = new TemplateFillConfig();

                    String result = processSingleTemplate(templateFile, readFiles, preprocessedSummaries, userId,
                            fileService, userConfigService, apiService, isWordTemplate,
                            tempFileIds, userDescription, config);

                    if (result != null && result.contains("成功")) {
                        successCount++;
                        templateSuccess = true;
                        log.info("模板 {} 处理成功", templateFile.getOriginalName());
                    } else {
                        failureCount++;
                        failedTemplates.add(templateFile.getOriginalName() + ": " + result);
                        log.warn("模板 {} 处理失败：{}", templateFile.getOriginalName(), result);
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
                            try {
                                fileService.deleteFileById(fileId, userId);
                            } catch (Exception ex) {
                                log.warn("删除临时文件失败：{}", fileId, ex);
                            }
                        }
                    }
                }
            }

            log.info("\n========== 多模板智能填充完成 ==========");
            log.info("成功：{}/{}", successCount, templateFiles.size());
            log.info("失败：{}/{}", failureCount, templateFiles.size());

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
            File templateFile, List<File> readFiles, List<AISwitchFilesForFormFiller.FileSummary> fileSummaries,
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

        Map<Integer, List<String>> fileMapping = AISwitchFilesForFormFiller.decideFileMapping(
                tablesOrSheets, fileSummaries, userDescription, userId, userConfigService, apiService, fileService);
        String mappingFileId = saveFileMapping(fileMapping, templateFile.getId(), userId, fileService);
        tempFileIds.add(mappingFileId);

        // ========== 判断是否可使用 ExcelToTemplate ==========
        boolean useExcelToTemplate = false;
        File singleExcelFile = null;

        // 收集所有映射的文件 ID（去重）
        Set<String> mappedFileIds = new HashSet<>();
        for (List<String> ids : fileMapping.values()) {
            if (ids != null) {
                mappedFileIds.addAll(ids);
            }
        }

        // 条件：映射文件唯一且为 Excel
        if (mappedFileIds.size() == 1) {
            String uniqueFileId = mappedFileIds.iterator().next();
            // 从 fileSummaries 中找到对应的摘要
            AISwitchFilesForFormFiller.FileSummary summary = fileSummaries.stream()
                    .filter(s -> s.getFileId().equals(uniqueFileId))
                    .findFirst()
                    .orElse(null);
            if (summary != null && "Excel".equals(summary.getFileType())) {
                // 从 readFiles 中找到对应的 File 对象
                singleExcelFile = readFiles.stream()
                        .filter(f -> f.getId().equals(uniqueFileId))
                        .findFirst()
                        .orElse(null);
                if (singleExcelFile != null) {
                    useExcelToTemplate = true;
                    log.info("检测到当前模板所有表格均映射到同一个 Excel 文件 (ID: {}), 将使用 ExcelToTemplate.processWithAI 处理", uniqueFileId);
                }
            }
        }

        if (useExcelToTemplate) {
            String result = ExcelToTemplate.processWithAI(
                    singleExcelFile,
                    templateFile,
                    userId,
                    fileService,
                    userConfigService,
                    apiService,
                    userDescription,
                    isWordTemplate
            );

            // 清理当前已创建的临时文件（表头文件和映射文件）
            for (String fileId : tempFileIds) {
                try {
                    fileService.deleteFileById(fileId, userId);
                    log.debug("已删除临时文件：{}", fileId);
                } catch (Exception e) {
                    log.warn("删除临时文件失败：{}", fileId, e);
                }
            }

            // 根据结果返回，符合上层期望
            if (result != null && result.contains("✅")) {
                log.info("模板 {} 使用 ExcelToTemplate 处理成功", templateFile.getOriginalName());
                return "成功";
            } else {
                log.warn("模板 {} 使用 ExcelToTemplate 处理失败：{}", templateFile.getOriginalName(), result);
                return result; // 返回具体错误信息
            }
        }

        // 2.3 创建模板副本
        log.info("[Step 2.3] 开始创建模板副本...");
        String filledFileId = createTemplateCopy(templateFile, userId, fileService, isWordTemplate);
        if (filledFileId == null) return "创建模板副本失败";
        tempFileIds.add(filledFileId);

        // 2.4 按表格/工作表并行处理
        log.info("[Step 2.4] 开始并行处理各表格...");
        String conflictStrategy = config.getPrimaryKeyConflictStrategy();
        if (conflictStrategy == null) conflictStrategy = PRIMARY_KEY_CONFLICT_STRATEGY;

        Map<Integer, List<Object[]>> mergedTableData = processTablesParallel(
                tablesOrSheets, fileMapping, fileSummaries, userId,
                fileService, userConfigService, apiService,
                templateFile.getId(), tempFileIds, isWordTemplate, conflictStrategy, userDescription);

        // 2.5 填充模板
        log.info("[Step 2.5] 开始填充模板...");
        String updatedFileId = fillTemplate(filledFileId, mergedTableData, headerInfo,
                userId, fileService, isWordTemplate, templateFile.getOriginalName());
        tempFileIds.remove(filledFileId);
        tempFileIds.add(updatedFileId);
        filledFileId = updatedFileId;

        // 2.6 清理临时文件并移动结果
        log.info("[Step 2.6] 开始清理临时文件并移动结果...");
        cleanupAndMoveResult(updatedFileId, tempFileIds, userId, fileService);

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
            List<AISwitchFilesForFormFiller.FileSummary> fileSummaries, String userId,
            FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String templateFileId, Set<String> tempFileIds, boolean isWordTemplate,
            String conflictStrategy, String userDescription) throws Exception {

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
                                templateFileId, tempFileIds, isWordTemplate, conflictStrategy, userDescription);
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

    // ==================== 单个表格处理（支持混合文件源）====================
    private static List<Object[]> processSingleTable(
            int tableIndex, Map<String, Object> tableInfo, List<String> fileIds,
            List<AISwitchFilesForFormFiller.FileSummary> fileSummaries, String userId,
            FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String templateFileId, Set<String> tempFileIds, boolean isWordTemplate,
            String conflictStrategy, String userDescription) throws Exception {

        if (fileIds == null || fileIds.isEmpty()) return new ArrayList<>();

        List<String> headers = (List<String>) tableInfo.get("headers");
        int colCount = headers.size();

        // 获取模板文件名（用于 AI 上下文）
        String templateFileName = "未知模板";
        try {
            File templateFile = fileService.getFileById(templateFileId, userId);
            if (templateFile != null) {
                templateFileName = templateFile.getOriginalName();
            }
        } catch (Exception e) {
            log.warn("获取模板文件名失败：{}", templateFileId, e);
        }

        // 确定主键列索引
        Integer pkColIndex = null;
        String pkHeader = (String) tableInfo.get("primaryKeyHeader");
        if (pkHeader != null) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equals(pkHeader)) {
                    pkColIndex = i;
                    break;
                }
            }
        }

        // 收集每个文件的行数据（用于后续合并）
        List<FileRows> allFileRows = new ArrayList<>();

        for (String fileId : fileIds) {
            // 查找对应的 FileSummary
            AISwitchFilesForFormFiller.FileSummary summary = fileSummaries.stream()
                    .filter(s -> s.getFileId().equals(fileId))
                    .findFirst()
                    .orElse(null);
            if (summary == null) {
                log.warn("表格 {}: 文件 ID {} 不在摘要列表中，跳过", tableIndex, fileId);
                continue;
            }

            String fileType = summary.getFileType();

            if ("Excel".equals(fileType)) {
                // 处理 Excel 文件
                List<Object[]> excelRows = processExcelFile(
                        fileId, tableInfo, headers, pkColIndex,
                        userId, fileService, userConfigService, apiService,
                        userDescription, tempFileIds,
                        templateFileName, tableIndex);
                allFileRows.add(FileRows.forFlatRows(excelRows, false));
            } else {
                // 处理非 Excel 文件（文本文件）
                List<RowWithSegment> textRowsWithSegment = processTextFile(
                        fileId, tableInfo, headers, pkColIndex,
                        userId, fileService, userConfigService, apiService,
                        isWordTemplate, templateFileId, tableIndex, tempFileIds,
                        conflictStrategy);
                allFileRows.add(FileRows.forRowsWithSegment(textRowsWithSegment, true));
            }
        }

        // 合并所有文件的行数据
        return mergeAllDataWithFileBoundary(allFileRows, pkColIndex, colCount, conflictStrategy, headers);
    }

    /**
     * 处理 Excel 文件：解析数据 -> AI 筛选 -> 转换为 Object[] 列表
     */
    private static List<Object[]> processExcelFile(
            String fileId, Map<String, Object> tableInfo, List<String> headers, Integer pkColIndex,
            String userId, FileService fileService, UserConfigService userConfigService, ApiService apiService,
            String userDescription, Set<String> tempFileIds,
            String templateFileName, int tableIndex) throws Exception {

        // 获取文件内容
        AISwitchFilesForFormFiller.FileContentWrapper wrapper = AISwitchFilesForFormFiller.getFileContent(fileId, userId, fileService);
        if (!"Excel".equals(wrapper.getFileType())) {
            throw new Exception("文件类型不是 Excel: " + fileId);
        }

        // 解析 Excel 数据
        List<Map<String, Object>> excelData = ExcelDataProcessor.parseExcelToData(wrapper.getContent());

        if (excelData.isEmpty()) {
            System.out.println("Excel 文件 " + fileId + " 无数据");
            return new ArrayList<>();
        }

        // 准备样本数据（前 EXCEL_SAMPLE_ROWS 行）
        List<Map<String, Object>> sampleData = excelData.subList(0, Math.min(EXCEL_SAMPLE_ROWS, excelData.size()));

        // 确定当前表格名称
        String sheetName = (String) tableInfo.get("sheetName");
        String currentSheetName = sheetName != null ? sheetName : ("表格" + (tableIndex + 1));

        // 调用 AI 解析筛选条件，传入模板文件名和工作表名称
        ExcelDataProcessor.FilterCriteria filterCriteria = ExcelDataProcessor.parseFilterCriteria(
                userDescription, sampleData, headers,
                (String) tableInfo.get("primaryKeyHeader"),
                userId, userConfigService, apiService,
                templateFileName, currentSheetName);

        // 应用筛选
        List<Map<String, Object>> filteredData = ExcelDataProcessor.applyFilter(excelData, filterCriteria);

        System.out.println("Excel 文件 " + fileId + " 筛选后剩余 " + filteredData.size() + " 行");

        // 转换为 Object[] 列表（按 headers 顺序取值）
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> rowMap : filteredData) {
            Object[] rowArray = new Object[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                rowArray[i] = rowMap.get(headers.get(i));
            }
            rows.add(rowArray);
        }

        log.info("Excel 文件 {} 处理后获得 {} 行", fileId, rows.size());
        return rows;
    }

    /**
     * 处理文本文件：分段 -> AI 提取 -> 段边界合并 -> 返回带段号的行列表
     */
    private static List<RowWithSegment> processTextFile(
            String fileId, Map<String, Object> tableInfo, List<String> headers, Integer pkColIndex,
            String userId, FileService fileService, UserConfigService userConfigService, ApiService apiService,
            boolean isWordTemplate, String templateFileId, int tableIndex, Set<String> tempFileIds,
            String conflictStrategy) throws Exception {

        // 获取文件内容（文本）
        AISwitchFilesForFormFiller.FileContentWrapper wrapper = AISwitchFilesForFormFiller.getFileContent(fileId, userId, fileService);
        if (!wrapper.isText()) {
            throw new Exception("文件内容不是文本: " + fileId);
        }
        String textContent = new String(wrapper.getContent(), StandardCharsets.UTF_8);

        // 分段
        List<TextSegment> segments = segmentText(textContent);
        log.info("文件 {} 切割为 {} 个段落", fileId, segments.size());

        // 并行 AI 提取，得到带段号的 SegmentResult 列表
        List<SegmentResult> segmentResults = extractDataWithAI(
                segments, tableInfo, userId, fileService, userConfigService, apiService,
                templateFileId, tableIndex, tempFileIds, isWordTemplate);

        // 将所有段的行数据转换为 RowWithSegment 列表
        List<RowWithSegment> allRowsWithSegment = new ArrayList<>();
        for (SegmentResult seg : segmentResults) {
            if (seg.getRows() == null) continue;
            for (Map<String, Object> rowMap : seg.getRows()) {
                Object[] rowArray = new Object[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    rowArray[i] = rowMap.get(headers.get(i));
                }
                allRowsWithSegment.add(new RowWithSegment(rowArray, seg.getSegmentId()));
            }
        }

        // 段边界合并（针对单个文本文件）
        List<RowWithSegment> mergedForFile = mergeRowsWithinFile(allRowsWithSegment, pkColIndex, headers.size(), conflictStrategy);

        log.info("文本文件 {} 段边界合并后获得 {} 行", fileId, mergedForFile.size());
        return mergedForFile;
    }

    // ==================== 文本分段与 AI 提取 ====================
    private static List<TextSegment> segmentText(String fullText) {
    int totalLen = fullText.length();
    // 动态计算段大小：确保最多不超过 MAX_SEGMENTS 段，同时不小于最小段大小 SEGMENT_SIZE
    int dynamicSegmentSize = Math.max(SEGMENT_SIZE, (int) Math.ceil((double) totalLen / MAX_SEGMENTS));
    List<TextSegment> segments = new ArrayList<>();
    int nextStart = 0, segIndex = 0;
    while (nextStart < totalLen) {
        int segStart = nextStart;
        int segEnd = Math.min(nextStart + dynamicSegmentSize, totalLen);
        if (segIndex > 0 && segStart > 0)
            segStart = Math.max(0, segStart - OVERLAP_SIZE);
        if (segEnd < totalLen) {
            int lastNewline = fullText.lastIndexOf('\n', segEnd);
            if (lastNewline > segStart)
                segEnd = lastNewline + 1;
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
                ? userConfig.getSiliconFlowBaseUrl()
                : "https://api.siliconflow.com/v1";
        String modelName = (userConfig != null && userConfig.getAnalysisModelName() != null)
                ? userConfig.getAnalysisModelName()
                : "deepseek-ai/DeepSeek-V3.2";
        if (apiKey == null || apiKey.isEmpty()) throw new Exception("未配置 API Key");

        StringBuilder prompt = new StringBuilder();
        prompt.append("请从以下文本中提取表格数据。\n\n");

        List<String> headers = (List<String>) tableInfo.get("headers");
        prompt.append("表头：").append(String.join("，", headers)).append("\n\n");

        if (isWordTemplate) {
            if (tableInfo.containsKey("sheetDiscription1") && !((String) tableInfo.get("sheetDiscription1")).isEmpty()) {
                prompt.append("上文背景：").append(tableInfo.get("sheetDiscription1")).append("\n");
            }
            if (tableInfo.containsKey("sheetDiscription2") && !((String) tableInfo.get("sheetDiscription2")).isEmpty()) {
            prompt.append("下文背景：").append(tableInfo.get("sheetDiscription2")).append("\n");
            }
        }

        prompt.append("文本编号：").append(segment.getSegmentId()).append("\n");
        prompt.append("文本内容：\n").append(segment.getContent()).append("\n\n");

        prompt.append("请严格按以下 JSON 格式返回结果，不要包含任何额外说明、注释或 Markdown 标记：\n");
        prompt.append("{\n");
        prompt.append("  \"segmentId\": ").append(segment.getSegmentId()).append(",\n");
        prompt.append("  \"primaryKeyColumn\": \"主键列名，若无则为 null\",\n");
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

        prompt.append("核心要求：\n");
        prompt.append("1. 输出仅为纯 JSON，不得包含任何额外文字、注释或代码块标记。\n");
        prompt.append("2. 每行数据对应 rows 数组中的一个对象，对象的键为表头列名，值为该列对应内容。\n");
        prompt.append("3. 若某单元格无有效数据，请填写 null。\n");
        prompt.append("4. 若文本中存在明确的主键列（如 ID、编号、名称等唯一标识），请在 primaryKeyColumn 中填写该列名；否则填写 null。\n");
        prompt.append("5. 根据表头及上下文推断表格主题，仅提取与表头强相关的内容，避免引入无关信息。\n\n");

        prompt.append("单位与格式处理规则：\n");
        prompt.append("- 若表头已含单位（如“销售额(万元)”），单元格仅填数值（如“100”）。\n");
        prompt.append("- 若表头无单位，但原文数值自带单位（如“100万元”），则保留单位。\n");
        prompt.append("- 年份、月份、百分比、比率、编号等特殊数值，即使表头无单位，也不添加单位，保持原始格式。\n");
        prompt.append("- 若数值无单位且表头无单位，仅填数值。\n");
        prompt.append("- 同一列的数据格式应保持一致。\n");
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
        if (root.has("segmentId"))
            result.setSegmentId(root.get("segmentId").asInt(segmentId));
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

    // ==================== 合并算法 ====================

    /**
     * 单个文本文件内部的段边界合并（复用原 TxtToTemplate 的合并逻辑）
     */
    private static List<RowWithSegment> mergeRowsWithinFile(
            List<RowWithSegment> rowsWithSegment, Integer pkColIndex, int colCount, String conflictStrategy) {
        if (rowsWithSegment.isEmpty()) return new ArrayList<>();

        // 按段号分组
        Map<Integer, List<RowWithSegment>> segmentMap = new LinkedHashMap<>();
        for (RowWithSegment r : rowsWithSegment) {
            segmentMap.computeIfAbsent(r.segmentId, k -> new ArrayList<>()).add(r);
        }
        List<Integer> segmentIds = new ArrayList<>(segmentMap.keySet());
        Collections.sort(segmentIds);

        List<Object[]> processedRows = new ArrayList<>();

        for (int segId : segmentIds) {
            List<RowWithSegment> rowsInSegment = segmentMap.get(segId);
            // 段内去重（完全相同的行）
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

        // 将 List<Object[]> 转换为 List<RowWithSegment>，段号统一设为 -1 表示已合并
        List<RowWithSegment> result = new ArrayList<>();
        for (Object[] row : processedRows) {
            result.add(new RowWithSegment(row, -1));
        }
        return result;
    }

    private static RowWithSegment mergeTwoRows(
            RowWithSegment row1, RowWithSegment row2,
            Integer pkColIndex, int colCount, String conflictStrategy) {

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
                if (mergedRow[i] != null) {
                    allNull = false;
                    break;
                }
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

    /**
     * 辅助方法：将 List<RowWithSegment> 转换为 List<Object[]>
     */
    private static List<Object[]> rowsWithSegmentToObjects(List<RowWithSegment> rowsWithSegment) {
        List<Object[]> objects = new ArrayList<>();
        for (RowWithSegment r : rowsWithSegment) {
            objects.add(r.getRow());
        }
        return objects;
    }

    /**
     * 新的行合并算法：先对每个文件内部合并，再对相邻文件的首尾行进行边界合并，
     * 最后基于主键全局聚合。
     *
     * @param fileRowsList    各文件的行数据列表（已进行内部合并）
     * @param pkColIndex      主键列索引（可为 null）
     * @param colCount        总列数
     * @param conflictStrategy 冲突合并策略
     * @param headers         表头列表（仅用于日志）
     * @return 最终合并后的行列表（Object[] 形式）
     */
    private static List<Object[]> mergeAllDataWithFileBoundary(
            List<FileRows> fileRowsList, Integer pkColIndex, int colCount,
            String conflictStrategy, List<String> headers) {

        if (fileRowsList.isEmpty()) return new ArrayList<>();

        // Step 1: 将每个文件的行转换为扁平的 Object[] 列表（文本文件已内部合并）
        List<List<Object[]>> fileDataList = new ArrayList<>();
        for (FileRows fileRows : fileRowsList) {
            if (fileRows.isTextFile()) {
                fileDataList.add(rowsWithSegmentToObjects(fileRows.getRowsWithSegment()));
            } else {
                fileDataList.add(fileRows.getFlatRows());
            }
        }

        // Step 2: 文件间边界合并
        List<Object[]> buffer = new ArrayList<>();
        for (int i = 0; i < fileDataList.size(); i++) {
            List<Object[]> currentRows = fileDataList.get(i);
            if (currentRows.isEmpty()) continue;

            if (i == 0 || buffer.isEmpty()) {   // 增加 buffer.isEmpty() 判断，防止第一个文件为空时索引越界
                buffer.addAll(currentRows);
            } else {
                // 取上一个文件的最后一行与当前文件的第一行
                Object[] lastRowFromPrev = buffer.get(buffer.size() - 1);
                Object[] firstRowOfCurrent = currentRows.get(0);

                RowWithSegment prevWrapper = new RowWithSegment(lastRowFromPrev, -1);
                RowWithSegment currWrapper = new RowWithSegment(firstRowOfCurrent, -1);

                // 判断是否需要合并
                if (shouldMergeRows(prevWrapper, currWrapper, pkColIndex, colCount, conflictStrategy)) {
                    // 执行合并
                    RowWithSegment merged = mergeTwoRows(prevWrapper, currWrapper,
                            pkColIndex, colCount, conflictStrategy);
                    // 替换上一行
                    buffer.set(buffer.size() - 1, merged.getRow());
                    // 将当前文件剩余行加入缓冲区
                    for (int j = 1; j < currentRows.size(); j++) {
                        buffer.add(currentRows.get(j));
                    }
                } else {
                    // 不合并，直接追加当前文件所有行
                    buffer.addAll(currentRows);
                }
            }
        }

        // Step 3: 基于主键的全局聚合（复用现有逻辑）
        return aggregateRowsByPrimaryKey(buffer, pkColIndex, colCount, conflictStrategy, headers);
    }

    /**
     * 判断两行是否需要在文件边界处合并
     */
    private static boolean shouldMergeRows(RowWithSegment row1, RowWithSegment row2,
                                            Integer pkColIndex, int colCount, String conflictStrategy) {
        if (pkColIndex != null) {
            Object pk1 = row1.getRow()[pkColIndex];
            Object pk2 = row2.getRow()[pkColIndex];
            if (pk1 != null && pk2 != null && pk1.toString().equals(pk2.toString())) {
                return true; // 主键相同必须合并
            }
        }
        // 无主键或主键不同时，尝试用 MERGE 策略合并，看结果是否与两者均不同
        RowWithSegment merged = mergeTwoRows(row1, row2, pkColIndex, colCount, "MERGE");
        boolean sameAsRow1 = arraysEqualAsStrings(merged.getRow(), row1.getRow());
        boolean sameAsRow2 = arraysEqualAsStrings(merged.getRow(), row2.getRow());
        return !sameAsRow1 && !sameAsRow2;
    }

    /**
     * 基于主键的全局聚合（原 mergeDataFromMultipleSources 的第二阶段）
     */
    private static List<Object[]> aggregateRowsByPrimaryKey(
            List<Object[]> allRows, Integer pkColIndex, int colCount,
            String conflictStrategy, List<String> headers) {

        if (pkColIndex == null) {
            // 无主键：简单去重
            List<Object[]> unique = new ArrayList<>();
            for (Object[] row : allRows) {
                boolean found = false;
                for (Object[] ex : unique) {
                    if (arraysEqualAsStrings(row, ex)) {
                        found = true;
                        break;
                    }
                }
                if (!found) unique.add(row);
            }
            log.info("全局聚合完成（无主键），去重后共 {} 行", unique.size());
            return unique;
        } else {
            // 有主键：按主键聚合
            Map<Object, Object[]> aggregated = new LinkedHashMap<>();
            List<Object[]> nullPkRows = new ArrayList<>();

            for (Object[] row : allRows) {
                Object pkValue = (pkColIndex < row.length) ? row[pkColIndex] : null;
                if (pkValue == null) {
                    nullPkRows.add(row);
                    continue;
                }
                Object[] existing = aggregated.get(pkValue);
                if (existing == null) {
                    aggregated.put(pkValue, row);
                } else {
                    Object[] merged = mergeTwoRows(new RowWithSegment(existing, -1),
                            new RowWithSegment(row, -1), pkColIndex, colCount, conflictStrategy).row;
                    aggregated.put(pkValue, merged);
                }
            }

            List<Object[]> result = new ArrayList<>(aggregated.values());

            // 处理空主键行（简单去重）
            List<Object[]> uniqueNullPk = new ArrayList<>();
            for (Object[] row : nullPkRows) {
                boolean found = false;
                for (Object[] ex : uniqueNullPk) {
                    if (arraysEqualAsStrings(row, ex)) {
                        found = true;
                        break;
                    }
                }
                if (!found) uniqueNullPk.add(row);
            }
            result.addAll(uniqueNullPk);

            log.info("全局聚合完成（主键列：{}），合并后共 {} 行",
                    (pkColIndex < headers.size() ? headers.get(pkColIndex) : "未知"), result.size());
            return result;
        }
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
                                              String userId, FileService fileService) throws Exception {
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

                String fileName = f.getFileName();
                boolean isTextFile = fileName != null && fileName.endsWith("_text.txt");
                boolean isExcelSummary = fileName != null && fileName.endsWith("_summary.json");

                if (KEEP_TEXT_FILES && isTextFile) {
                    log.debug("保留文本文件: {}", fileName);
                    continue;
                }
                if (KEEP_EXCEL_SUMMARY && isExcelSummary) {
                    log.debug("保留 Excel 摘要文件: {}", fileName);
                    continue;
                }
                fileService.deleteFileById(fileId, userId);
                log.debug("已删除临时文件: {}", fileId);
            } catch (Exception e) {
                log.warn("处理临时文件失败：{}", fileId, e);
            }
        }
        log.info("临时文件清理完成，结果文件已保留在 result 区");
    }
}