package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文本到 Excel 的智能填表工具类
 * 处理文本文件到 Excel 模板的数据映射和填表操作
 */
public class TxtToExcel {
    
    private static final Logger log = LoggerFactory.getLogger(TxtToExcel.class);

    private static final int SEGMENT_SIZE = 30 * 1024; // 30KB
    private static final int OVERLAP_SIZE = 100; // 重叠字符数
    private static final int MAX_CONCURRENT_AI_CALLS = 10;
    private static final String AI_RESULT_FILE_PREFIX = "_ai_result_sheet";
    // 数据丢失标记常量
    private static final String MISSING_VALUE_MARKER = "<null>";
    
    /**
     * 处理文本到 Excel 的填表操作
     * @param readFile 读取区的文件（纯文本文件）
     * @param templateFile 模板区的 Excel 文件
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param userConfigService 用户配置服务（用于获取 API Key）
     * @return 处理结果描述
     */
    public static String process(File readFile, File templateFile, Long userId, 
                                  FileService fileService, UserConfigService userConfigService) {
        // 用于记录所有已创建的临时文件 ID
        Set<String> tempFileIds = new HashSet<>();
        String filledFileId = null;
        Map<Integer, Map<Integer, String>> resultFileMap = null;
        boolean success = false;
        
        try {
            log.info("========== 开始执行文本到 Excel 填表处理 ==========");
            log.info("读取文件：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
            log.info("模板文件：{} (ID: {})", templateFile.getOriginalName(), templateFile.getId());
            log.info("用户 ID: {}", userId);
            
            // 步骤 0: 提取模板表头信息并保存为 JSON（移至 HeaderExtractorUtil）
            log.info("【步骤 0】开始提取模板表头信息...");
            Map<String, Object> headerInfo = HeaderExtractorUtil.extractTemplateHeaders(templateFile, userId, fileService);
            if (headerInfo == null) {
                log.error("【步骤 0】提取模板表头信息失败");
                return "提取模板表头信息失败";
            }
            log.info("【步骤 0】表头信息提取成功，工作表数量：{}", ((List<?>)headerInfo.get("sheets")).size());
            // 将表头文件的真实 ID 加入临时集合（HeaderExtractorUtil 已返回保存后的文件 ID）
            String headerFileId = (String) headerInfo.get("fileId");
            tempFileIds.add(headerFileId);
            log.info("【步骤 0】表头文件 ID: {}", headerFileId);
            
            // 步骤 1: 准备数据 - 从数据库中查找对应的 txt 文件
            log.info("【步骤 1】开始从数据库查找读取文件...");
            String targetFileName = readFile.getId() + "_text.txt";
            File targetFile = findFileByFileName(readFile.getId(), targetFileName, userId, fileService);
            
            if (targetFile == null) {
                log.warn("【步骤 1】未找到对应的 txt 文件，预期文件名：{}，将调用 ExtractText 创建", targetFileName);
                
                // 读取原始文件内容
                byte[] originalContent = fileService.getFileContent(readFile.getId(), userId);
                if (originalContent == null) {
                    log.error("【步骤 1】获取原始文件内容失败");
                    return "获取原始文件内容失败";
                }
                
                try {
                    // 调用 ExtractText 转换为文本
                    log.info("【步骤 1】开始转换文件为文本格式...");
                    String textContent = ExtractText.convertToText(originalContent, readFile.getOriginalName());
                    log.info("【步骤 1】文件转换成功，文本长度：{}", textContent.length());
                    
                    // 将文本保存为新的 txt 文件
                    MultipartFile txtFile = new MultipartFile() {
                        @Override
                        public String getName() {
                            return targetFileName;
                        }

                        @Override
                        public String getOriginalFilename() {
                            return targetFileName;
                        }

                        @Override
                        public String getContentType() {
                            return "text/plain";
                        }

                        @Override
                        public boolean isEmpty() {
                            return false;
                        }

                        @Override
                        public long getSize() {
                            return textContent.getBytes(StandardCharsets.UTF_8).length;
                        }

                        @Override
                        public byte[] getBytes() {
                            return textContent.getBytes(StandardCharsets.UTF_8);
                        }

                        @Override
                        public ByteArrayInputStream getInputStream() {
                            return new ByteArrayInputStream(getBytes());
                        }

                        @Override
                        public void transferTo(java.io.File dest) {
                            // 不需要实现
                        }
                    };
                    
                    // 保存到 temp 区
                    targetFile = fileService.saveFile(txtFile, "temp", userId);
                    log.info("【步骤 1】txt 文件已创建并保存：{} (ID: {}, 大小：{} bytes)", 
                            targetFile.getOriginalName(), targetFile.getId(), targetFile.getSize());
                    
                } catch (Exception e) {
                    log.error("【步骤 1】文件转换失败：{}", e.getMessage(), e);
                    return "文件转换失败：" + e.getMessage();
                }
            }
            
            log.info("【步骤 1】找到目标文件：{} (ID: {}, 文件名：{})", 
                    targetFile.getOriginalName(), targetFile.getId(), targetFile.getFileName());
            
            byte[] readContent = fileService.getFileContent(targetFile.getId(), userId);
            if (readContent == null) {
                log.error("【步骤 1】获取读取文件内容失败");
                return "获取读取文件内容失败";
            }
            String fullText = new String(readContent, StandardCharsets.UTF_8);
            log.info("【步骤 1】读取文件内容成功，总字符数：{}", fullText.length());
            
            // 步骤 2: 创建模板副本
            log.info("【步骤 2】开始创建模板副本...");
            filledFileId = createTemplateCopy(templateFile, userId, fileService);
            if (filledFileId == null) {
                log.error("【步骤 2】创建模板副本失败");
                return "创建模板副本失败";
            }
            tempFileIds.add(filledFileId);
            log.info("【步骤 2】模板副本创建成功，文件 ID: {}", filledFileId);
            
            // 步骤 3: 文本切割
            log.info("【步骤 3】开始文本切割...");
            List<TextSegment> segments = segmentText(fullText);
            log.info("【步骤 3】文本已切分为 {} 个段落", segments.size());
            
            // // 步骤 3.5: 将切割后的文本段落保存到 result 区（用于调试和备份）
            // log.info("【步骤 3.5】开始保存文本段落到 result 区...");
            // saveSegmentsToResult(segments, readFile.getId(), userId, fileService);
            // log.info("【步骤 3.5】文本段落保存完成");
            
            // 步骤 4: 并行 AI 提取所有段数据（任一失败则抛出异常）
            log.info("【步骤 4】开始并行 AI 提取数据...");
            log.info("【步骤 4】使用原始模板文件 ID 构造 AI 结果文件名：{}", templateFile.getId());
            resultFileMap = extractDataWithAI(
                segments, headerInfo, userId, fileService, userConfigService, templateFile.getId());
            log.info("【步骤 4】AI 数据提取完成，成功处理 {} 个工作表", resultFileMap.size());
            
            // 将 AI 结果文件 ID 加入临时集合
            for (Map<Integer, String> segMap : resultFileMap.values()) {
                tempFileIds.addAll(segMap.values());
            }
            log.info("【步骤 4】已将 {} 个 AI 结果文件加入清理列表", tempFileIds.size() - 2); // 减 2 是因为前面已有 headerFileId 和 filledFileId
            
            // 步骤 5: 合并每个工作表的数据（从临时文件加载数据）
            log.info("【步骤 5】开始合并段落数据...");
            Map<String, List<Object[]>> mergedSheetData = mergeSegmentData(
                resultFileMap, headerInfo, userId, fileService);
            log.info("【步骤 5】数据合并完成，共 {} 个工作表", mergedSheetData.size());
            
            // 步骤 6: 串行写入每个工作表的数据到 Excel 副本（移至 JsonToExcelWriterUtil）
            log.info("【步骤 6】开始填充 Excel 模板...");
            String updatedFileId = JsonToExcelWriterUtil.fillExcelTemplate(filledFileId, mergedSheetData, headerInfo, userId, fileService);
            log.info("【步骤 6】Excel 填充完成，新文件 ID: {}", updatedFileId);
            
            // 更新临时文件记录：旧文件已被删除，新文件将移动到 result 区
            tempFileIds.remove(filledFileId);
            tempFileIds.add(updatedFileId);
            filledFileId = updatedFileId;
            
            // 步骤 7: 清理与返回（使用更新后的文件 ID）
            log.info("【步骤 7】开始清理临时文件并移动结果...");
            cleanupAndFinish(updatedFileId, templateFile.getId(), userId, fileService);
            
            // 标记成功，避免 finally 重复清理
            success = true;
            
            log.info("========== 文本到 Excel 填表处理完成 ==========");
            return "已完成智能填写操作";
            
        } catch (Exception e) {
            log.error("========== 文本到 Excel 填表处理失败 ==========", e);
            log.error("错误信息：{}", e.getMessage());
            log.error("错误类型：{}", e.getClass().getSimpleName());
            // 异常时，尝试清理表头文件和 AI 结果文件（通过文件名模式作为备用方案）
            try {
                deleteFileByName(templateFile.getId() + "_headers.json", "temp", userId, fileService);
                log.debug("已尝试删除表头文件（通过文件名）");
            } catch (Exception ex) {
                log.warn("通过文件名删除表头文件失败：{}", ex.getMessage());
            }
            try {
                deleteAIResultFiles(templateFile.getId(), "temp", userId, fileService);
                log.debug("已尝试删除 AI 结果文件（通过文件名）");
            } catch (Exception ex) {
                log.warn("通过文件名删除 AI 结果文件失败：{}", ex.getMessage());
            }
            return "文本到 Excel 填表处理失败：" + e.getMessage();
        } finally {
            // 若发生异常，清理所有临时文件（通过真实文件 ID）
            if (!success) {
                log.info("检测到异常，开始清理临时文件（共 {} 个）", tempFileIds.size());
                for (String fileId : tempFileIds) {
                    try {
                        fileService.deleteFileById(fileId, userId);
                        log.debug("已删除临时文件：{}", fileId);
                    } catch (Exception ex) {
                        log.warn("删除临时文件失败：{} - {}", fileId, ex.getMessage());
                    }
                }
                log.info("临时文件清理完成");
            }
        }
    }
    
    /**
     * 步骤 2: 创建模板副本
     */
    private static String createTemplateCopy(File templateFile, Long userId, FileService fileService) 
            throws Exception {
        log.info("步骤 2: 创建模板副本");
        
        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) {
            return null;
        }
        
        MultipartFile copyFile = new MultipartFile() {
            @Override
            public String getName() {
                return templateFile.getId() + "_filled.xlsx";
            }

            @Override
            public String getOriginalFilename() {
                return templateFile.getId() + "_filled.xlsx";
            }

            @Override
            public String getContentType() {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return templateContent.length;
            }

            @Override
            public byte[] getBytes() {
                return templateContent;
            }

            @Override
            public ByteArrayInputStream getInputStream() {
                return new ByteArrayInputStream(templateContent);
            }

            @Override
            public void transferTo(java.io.File dest) {
                // 不需要实现
            }
        };
        
        File savedFile = fileService.saveFile(copyFile, "temp", userId);
        log.info("模板副本已创建：{}", savedFile.getId());
        
        return savedFile.getId();
    }
    
    /**
     * 根据 fileName 查找对应的文件
     * @param readFileId 读取文件的 ID
     * @param targetFileName 目标文件名（格式：{readFileId}_text.txt）
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 找到的文件对象，未找到返回 null
     */
    private static File findFileByFileName(String readFileId, String targetFileName, 
                                           Long userId, FileService fileService) {
        try {
            // 从 temp 区查找所有文件
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");
            
            // 遍历查找匹配的文件
            for (File file : tempFiles) {
                if (file.getFileName() != null && file.getFileName().equals(targetFileName)) {
                    log.debug("找到匹配的文件：ID={}, fileName={}, originalName={}", 
                             file.getId(), file.getFileName(), file.getOriginalName());
                    return file;
                }
            }
            
            // 如果在 temp 区没找到，尝试在 result 区查找
            List<File> resultFiles = fileService.getFilesByUserIdAndSection(userId, "result");
            for (File file : resultFiles) {
                if (file.getFileName() != null && file.getFileName().equals(targetFileName)) {
                    log.debug("在 result 区找到匹配的文件：ID={}, fileName={}, originalName={}", 
                             file.getId(), file.getFileName(), file.getOriginalName());
                    return file;
                }
            }
            
            log.debug("未找到匹配的文件：{}", targetFileName);
            return null;
            
        } catch (Exception e) {
            log.error("查找文件失败：{} - {}", targetFileName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 步骤 3: 文本切割（优化重叠实现）
     * 除第一段外，所有段都包含前一段的最后 OVERLAP_SIZE 个字符作为上下文
     */
    private static List<TextSegment> segmentText(String fullText) {
        log.info("步骤 3: 文本切割");
        
        List<TextSegment> segments = new ArrayList<>();
        int nextStart = 0; // 下一段的起始位置（无重叠）
        int segmentIndex = 0;
        
        while (nextStart < fullText.length()) {
            int segStart = nextStart;
            int segEnd = Math.min(nextStart + SEGMENT_SIZE, fullText.length());
            
            // 如果不是第一段，向前包含重叠部分（包括最后一段）
            if (segmentIndex > 0) {
                segStart = Math.max(0, segStart - OVERLAP_SIZE);
            }
            
            String content = fullText.substring(segStart, segEnd);
            
            TextSegment segment = new TextSegment();
            segment.setSegmentId(segmentIndex);
            segment.setContent(content);
            segment.setStartPos(segStart);
            segment.setEndPos(segEnd);
            
            segments.add(segment);
            log.debug("创建段落 {}: 起始={}, 结束={}, 长度={}", 
                     segmentIndex, segStart, segEnd, content.length());
            
            nextStart = segEnd; // 下一段无重叠起始为当前段结束
            segmentIndex++;
        }
        
        return segments;
    }
    
    /**
     * 步骤 3.5: 将切割后的文本段落保存到 result 区（用于调试和备份）
     * 文件命名格式：{readFileId}_段落{x}.txt
     */
    private static void saveSegmentsToResult(List<TextSegment> segments, String readFileId, 
                                              Long userId, FileService fileService) throws Exception {
        for (TextSegment segment : segments) {
            try {
                // 生成文件名：{readFileId}_段落{x}.txt
                String fileName = readFileId + "_段落" + segment.getSegmentId() + ".txt";
                
                // 创建 MultipartFile
                byte[] contentBytes = segment.getContent().getBytes(StandardCharsets.UTF_8);
                MultipartFile segmentFile = JsonToExcelWriterUtil.createMultipartFile(
                    fileName,
                    fileName,
                    "text/plain",
                    contentBytes
                );
                
                // 保存到 result 区
                File savedFile = fileService.saveFile(segmentFile, "result", userId);
                log.info("已保存段落 {} 到 result 区：{} (ID: {}, 大小：{} bytes)", 
                        segment.getSegmentId(), fileName, savedFile.getId(), contentBytes.length);
            } catch (Exception e) {
                log.error("保存段落 {} 失败：{}", segment.getSegmentId(), e.getMessage());
                // 继续保存其他段落，不中断整个流程
            }
        }
    }
    
    /**
     * 步骤 4: 并行 AI 提取所有段数据（将结果保存为临时文件，返回文件 ID）
     * 注意：即使部分任务失败，也会返回已成功的结果文件 ID 用于清理
     */
    private static Map<Integer, Map<Integer, String>> extractDataWithAI(
            List<TextSegment> segments, Map<String, Object> headerInfo,
            Long userId, FileService fileService, UserConfigService userConfigService,
            String originalTemplateFileId) throws Exception {
        log.info("步骤 4: 并行 AI 提取数据");
        
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");
        // 注意：headerInfo.get("fileId") 现在是表头 JSON 文件的 ID，不能用于构造 AI 结果文件名
        // 必须使用原始模板文件 ID 来构造文件名，确保与 cleanupAndFinish 一致
        Map<Integer, Map<Integer, String>> resultFileMap = new ConcurrentHashMap<>();
        
        // 使用 AtomicBoolean 记录是否有任务失败
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        // 用于存储第一个失败的异常
        AtomicReference<Exception> failureException = new AtomicReference<>();
        
        // 使用线程池控制并发
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_AI_CALLS);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_AI_CALLS);
        // 可配置的超时时间，默认 10 分钟
        long timeoutMinutes = Long.parseLong(System.getProperty("ai.extract.timeout.minutes", "10"));
        CountDownLatch latch = new CountDownLatch(segments.size() * sheets.size());
        
        try {
            for (int segIndex = 0; segIndex < segments.size(); segIndex++) {
                TextSegment segment = segments.get(segIndex);
                
                for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
                    final int segIdx = segIndex;
                    final int sheetIdx = sheetIndex;
                    
                    executor.submit(() -> {
                        try {
                            semaphore.acquire();
                            
                            try {
                                SegmentResult result = callAIForSegment(
                                    segment, 
                                    (Map<String, Object>) sheets.get(sheetIdx),
                                    userId,
                                    fileService,
                                    userConfigService
                                );
                                
                                // 验证结果是否有效
                                if (result == null || result.getCellData() == null || result.getCellData().isEmpty()) {
                                    log.warn("段落{}，工作表{}：AI 返回的数据为空或无效", segIdx, sheetIdx);
                                    // 不抛出异常，继续处理其他段落
                                } else {
                                    // 将结果保存到临时文件，获取文件 ID（使用原始模板文件 ID 构造文件名）
                                    String fileId = saveAIResultToTemp(result, segIdx, sheetIdx, originalTemplateFileId, userId, fileService);
                                    
                                    // 存入结果 Map
                                    resultFileMap.computeIfAbsent(sheetIdx, k -> new ConcurrentHashMap<>())
                                                .put(segIdx, fileId);
                                    
                                    log.info("AI 提取完成 - 段落{}，工作表{}，结果文件：{}, 单元格数：{}", 
                                            segIdx, sheetIdx, fileId, result.getCellData().size());
                                }
                                
                            } catch (Exception e) {
                                // 记录失败状态和异常
                                log.error("AI 提取失败 - 段落{}，工作表{}: {}", segIdx, sheetIdx, e.getMessage(), e);
                                // 不立即抛出异常，让其他段落继续处理
                            } finally {
                                semaphore.release();
                                latch.countDown();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            if (hasFailure.compareAndSet(false, true)) {
                                failureException.set(e);
                            }
                            latch.countDown();
                        } catch (Exception e) {
                            // 处理 semaphore.acquire() 阶段的异常
                            if (hasFailure.compareAndSet(false, true)) {
                                failureException.set(e);
                            }
                            latch.countDown();
                        }
                    });
                }
            }
            
            // 等待所有任务完成
            if (!latch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                log.error("AI 数据提取超时 ({}分钟)", timeoutMinutes);
                throw new TimeoutException("AI 数据提取超时 (" + timeoutMinutes + "分钟)");
            }
            
            // 统计并记录任务完成情况
            int totalTasks = segments.size() * sheets.size();
            int successfulTasks = resultFileMap.values().stream().mapToInt(Map::size).sum();
            int failedTasks = totalTasks - successfulTasks;
            
            if (failedTasks > 0) {
                log.warn("AI 数据提取完成，但有 {} 个任务失败（共{}个）", failedTasks, totalTasks);
                // 输出失败的段落信息
                for (int sheetIdx = 0; sheetIdx < sheets.size(); sheetIdx++) {
                    Map<Integer, String> segMap = resultFileMap.getOrDefault(sheetIdx, new HashMap<>());
                    for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
                        if (!segMap.containsKey(segIdx)) {
                            log.warn("失败任务：段落{}，工作表{}", segIdx, sheetIdx);
                        }
                    }
                }
            } else {
                log.info("AI 数据提取全部成功（共{}个任务）", successfulTasks);
            }
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("AI 数据提取完成，共处理 {} 个段落 × {} 个工作表，成功 {} 个", 
                segments.size(), sheets.size(), resultFileMap.values().stream().mapToInt(Map::size).sum());
        return resultFileMap;
    }
    
    /**
     * 保存 AI 提取结果到临时文件（返回文件 ID）
     */
    private static String saveAIResultToTemp(SegmentResult result, int segmentId, int sheetId,
                                             String templateFileId, Long userId, FileService fileService) throws Exception {
        // 将结果序列化为 JSON
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(result);
        
        // 生成文件名：{templateFileId}_ai_result_sheet{sheetId}_seg{segmentId}.json
        String fileName = templateFileId + AI_RESULT_FILE_PREFIX + sheetId + "_seg" + segmentId + ".json";
        
        // 调用 JsonToExcelWriterUtil 的工具方法创建 MultipartFile
        MultipartFile aiResultFile = JsonToExcelWriterUtil.createMultipartFile(
            fileName, 
            fileName, 
            "application/json", 
            jsonContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 保存到临时区
        File savedFile = fileService.saveFile(aiResultFile, "temp", userId);
        log.debug("AI 结果已持久化到临时文件：{} (ID: {})", fileName, savedFile.getId());
        
        return savedFile.getId();
    }
    
    /**
     * 调用 AI 接口提取单个段落的数据
     */
    private static SegmentResult callAIForSegment(TextSegment segment, 
                                                   Map<String, Object> sheetInfo,
                                                   Long userId, FileService fileService,
                                                   UserConfigService userConfigService) throws Exception {
        String sheetName = (String) sheetInfo.get("sheetName");
        List<String> headers = (List<String>) sheetInfo.get("headers");
        
        // 构建 AI 提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请从以下文本中提取表格数据，表头为：").append(String.join(", ", headers));
        prompt.append("\n工作表名称：").append(sheetName);
        prompt.append("\n文本内容编号：").append(segment.getSegmentId());
        prompt.append("\n\n文本内容:\n").append(segment.getContent());
        prompt.append("\n\n请严格按照以下 JSON 格式返回结果:\n");
        prompt.append("{\n");
        prompt.append("  \"文本内容编号\": ").append(segment.getSegmentId()).append(",\n");
        prompt.append("  \"总行数\": <数字>,\n");
        prompt.append("  \"主键列\": <数字>,\n");
        prompt.append("  \"表格内容\": {\n");
        prompt.append("    \"R0C0\": \"单元格内容\",\n");
        prompt.append("    \"R0C1\": \"单元格内容\",\n");
        prompt.append("    ...\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("重要要求：\n");
        prompt.append("1. 必须直接返回纯 JSON 数据，不要有任何其他文字、说明或标记\n");
        prompt.append("2. 禁止使用 Markdown 代码块（不要用 ```json 包裹）\n");
        prompt.append("3. 单元格位置用 RxCy 表示，x 是行号，y 是列号\n");
        prompt.append("4. 如果某个单元格没有数据，填<null>\n");
        prompt.append("5. 表格内容中不要出现表头，仅包含单元格数据\n");
        prompt.append("6. 确保 JSON 格式完整且可解析\n");
        prompt.append("7. **极其重要**: 必须返回完整的 JSON，不得在中间截断\n");
        prompt.append("8. **极其重要**: 所有键名和字符串值必须使用英文双引号 \" 包裹\n");
        prompt.append("9. **极其重要**: 确保所有中文字符正确编码，不出现乱码\n");
        prompt.append("10. 如果数据量较大，请确保完整输出，即使超过常规长度限制\n");
        
        // 调用 AI 服务（从用户配置中获取 API Key 和模型名称）
        ApiService apiService = new ApiService();
        String apiKey = getApiKeyFromUserConfig(userId, userConfigService);
        String apiUrl = getApiUrlFromUserConfig(userId, userConfigService);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("未配置 API Key，请先在用户配置中设置API Key");
        }
        
        // 从用户配置中获取分析模型名称
        String modelName = getAnalysisModelNameFromUserConfig(userId, userConfigService);
        
        String aiResponse = apiService.callExternalApi(
            apiUrl,
            apiKey,
            prompt.toString(),
            modelName
        );
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            throw new Exception("AI 服务返回空响应 - 段落" + segment.getSegmentId());
        }
        
        // 解析 AI 响应
        SegmentResult result = parseAIResponse(aiResponse, segment.getSegmentId());
        
        return result;
    }
    
    /**
     * 从用户配置中获取 API Key
     */
    private static String getApiKeyFromUserConfig(Long userId, UserConfigService userConfigService) {
        // 获取用户的配置
        com.project.aidoc.entity.UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowApiKey() == null ||
                userConfig.getSiliconFlowApiKey().isEmpty()) {
            // 如果没有配置 API 密钥，返回 null
            log.warn("用户 {} 未配置 API Key", userId);
            return null;
        }
        
        log.debug("从用户配置获取到 API Key");
        return userConfig.getSiliconFlowApiKey();
    }

    private static String getApiUrlFromUserConfig(Long userId, UserConfigService userConfigService) {
        com.project.aidoc.entity.UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowBaseUrl() == null ||
                userConfig.getSiliconFlowBaseUrl().isEmpty()) {
            log.warn("用户 {} 未配置 API URL，使用默认值：https://api.siliconflow.com/v1", userId);
            return "https://api.siliconflow.com/v1";
        }
        
        log.debug("从用户配置获取到 API URL");
        return userConfig.getSiliconFlowBaseUrl();
    }
    /**
     * 从用户配置中获取分析模型名称
     */
    private static String getAnalysisModelNameFromUserConfig(Long userId, UserConfigService userConfigService) {
        // 获取用户的配置
        com.project.aidoc.entity.UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getAnalysisModelName() == null ||
                userConfig.getAnalysisModelName().isEmpty()) {
            // 如果没有配置模型名称，使用默认值并记录警告日志
            log.warn("用户 {} 未配置 analysisModelName，使用默认值：deepseek-ai/DeepSeek-V3.2", userId);
            return "deepseek-ai/DeepSeek-V3.2";
        }
        
        log.debug("从用户配置获取到 analysisModelName: {}", userConfig.getAnalysisModelName());
        return userConfig.getAnalysisModelName();
    }
    
    /**
     * 解析 AI 响应的 JSON 数据（增强容错性）
     */
    private static SegmentResult parseAIResponse(String aiResponse, int segmentId) throws Exception {
        SegmentResult result = new SegmentResult();
        result.setSegmentId(segmentId);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            // 配置 ObjectMapper 以允许某些格式问题
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            // 记录原始 AI 响应（前 500 字符）用于调试
            log.info("AI 原始响应前 500 字符：{}", 
                aiResponse.length() > 500 ? aiResponse.substring(0, 500) + "..." : aiResponse);
            
            // 尝试从响应中提取第一个 JSON 对象（处理 AI 可能返回额外文本的情况）
            String jsonContent = extractJsonFromResponse(aiResponse);
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                log.error("无法提取 JSON - AI 响应内容：{}", aiResponse);
                throw new Exception("无法从 AI 响应中提取有效 JSON");
            }
            
            log.debug("提取到的 JSON 内容：{}", jsonContent);
            
            // 尝试修复常见的 JSON 格式问题（如未引用的键名）
            jsonContent = fixCommonJsonIssues(jsonContent);
            
            JsonNode rootNode = mapper.readTree(jsonContent);
            
            // 解析"文本内容编号"（可选字段，使用默认值）
            if (rootNode.has("文本内容编号")) {
                result.setSegmentId(rootNode.get("文本内容编号").asInt(segmentId));
            } else {
                log.debug("AI 响应缺少'文本内容编号'字段，使用默认值：{}", segmentId);
                result.setSegmentId(segmentId);
            }
            
            // 解析"总行数"（可选字段，默认 0）
            int totalRows = rootNode.has("总行数") ? rootNode.get("总行数").asInt() : 0;
            result.setTotalRows(totalRows);
            
            // 解析"主键列"（新增）
            if (rootNode.has("主键列")) {
                result.setPrimaryKeyColumn(rootNode.get("主键列").asInt());
                log.debug("段落{} 主键列索引：{}", segmentId, result.getPrimaryKeyColumn());
            } else {
                log.warn("段落{} AI 响应缺少'主键列'字段，将使用默认主键列索引 0", segmentId);
                result.setPrimaryKeyColumn(0); // 默认第一列
            }
            
            // 解析"表格内容"（必需字段，但允许为空对象）
            Map<String, Object> cellData = new HashMap<>();
            JsonNode tableNode = rootNode.get("表格内容");
            
            if (tableNode != null && tableNode.isObject()) {
                tableNode.fields().forEachRemaining(entry -> {
                    String cellPos = entry.getKey();
                    // 验证单元格位置格式
                    if (!cellPos.matches("R\\d+C\\d+")) {
                        log.warn("无效的单元格位置：{}，已忽略", cellPos);
                        return;
                    }
                    
                    JsonNode valueNode = entry.getValue();
                    // JSON null 表示缺失，不加入 cellData
                    if (valueNode.isNull()) {
                        return;
                    }
                    
                    if (valueNode.isTextual()) {
                        String text = valueNode.asText();
                        // 字符串 "<null>" 也表示缺失，不加入 cellData
                        if (MISSING_VALUE_MARKER.equals(text)) {
                            return;
                        }
                        cellData.put(cellPos, text);
                    } else if (valueNode.isNumber()) {
                        // 保留 Number 类型，便于后续 Excel 写入时保持格式
                        cellData.put(cellPos, valueNode.numberValue());
                    } else if (valueNode.isBoolean()) {
                        // 保留 Boolean 类型，便于后续 Excel 写入时保持格式
                        cellData.put(cellPos, valueNode.booleanValue());
                    } else {
                        // 其他类型（数组、对象等）转为字符串
                        cellData.put(cellPos, valueNode.toString());
                    }
                });
            } else {
                log.warn("AI 响应缺少'表格内容'字段或格式不正确，使用空数据");
            }
            
            result.setCellData(cellData);
            
            log.info("解析 AI 响应 - 段落{}，总行数{}，单元格数{}", segmentId, totalRows, cellData.size());
            
        } catch (Exception e) {
            log.error("解析 AI 响应失败 - 段落{}，原始响应：{}", segmentId, aiResponse, e);
            // 抛出异常，使外层任务标记为失败并终止流程
            throw new Exception("解析 AI 响应失败 - 段落" + segmentId + ": " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 从 AI 响应文本中提取第一个完整的 JSON 对象
     * @param response AI 响应文本（可能包含额外说明文字、Markdown 代码块等）
     * @return 提取的 JSON 字符串，若未找到则返回 null
     */
    private static String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = response.trim();
        
        // 情况 1: 如果整个响应就是一个 JSON 对象
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        // 情况 2: 尝试移除 Markdown 代码块标记
        // 处理 ```json ... ``` 或 ``` ... ``` 格式
        String markdownCleaned = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        markdownCleaned = markdownCleaned.trim();
        
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        // 情况 3: 查找第一个 '{' 和最后一个 '}'（处理前后有额外文本的情况）
        int startIdx = trimmed.indexOf('{');
        int endIdx = trimmed.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            String jsonCandidate = trimmed.substring(startIdx, endIdx + 1);
            log.debug("从原始响应中提取到 JSON 候选：{} (长度:{})", 
                    jsonCandidate.length() > 100 ? jsonCandidate.substring(0, 100) + "..." : jsonCandidate, 
                    jsonCandidate.length());
            return jsonCandidate;
        }
        
        // 情况 4: 尝试从清理后的文本中提取
        startIdx = markdownCleaned.indexOf('{');
        endIdx = markdownCleaned.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            String jsonCandidate = markdownCleaned.substring(startIdx, endIdx + 1);
            log.debug("从清理后响应中提取到 JSON 候选：{} (长度:{})", 
                    jsonCandidate.length() > 100 ? jsonCandidate.substring(0, 100) + "..." : jsonCandidate, 
                    jsonCandidate.length());
            return jsonCandidate;
        }
        
        // 情况 5: 如果找不到闭合的 '}'，尝试找到最后一个逗号并截断（处理 JSON 被截断的情况）
        log.warn("检测到 JSON 可能被截断，尝试修复...");
        startIdx = trimmed.indexOf('{');
        if (startIdx >= 0) {
            // 从末尾向前找最后一个完整的键值对
            int lastComma = trimmed.lastIndexOf(',');
            if (lastComma > startIdx) {
                // 尝试在最后一个逗号处截断，并添加闭合括号
                String partialJson = trimmed.substring(startIdx, lastComma) + "}";
                log.debug("尝试修复截断的 JSON: {}", partialJson.length() > 100 ? partialJson.substring(0, 100) + "..." : partialJson);
                return partialJson;
            }
        }
        
        // 所有尝试都失败，记录详细日志以便调试
        log.warn("无法从响应中提取 JSON - 原始响应前 200 字符：{}", 
                trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
        
        return null;
    }
    
    /**
     * 尝试修复常见的 JSON 格式问题（如未引用的键名、截断的 JSON 等）
     */
    private static String fixCommonJsonIssues(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return jsonContent;
        }
        
        String fixed = jsonContent.trim();
        
        // 问题 1: 移除键名周围的多余空格
        fixed = fixed.replaceAll("([{,])\\s*\"", "$1\"");
        fixed = fixed.replaceAll("\"\\s*:", "\":");
        
        // 问题 2: 移除末尾多余的逗号
        fixed = fixed.replaceAll(",\\s*}", "}");
        fixed = fixed.replaceAll(",\\s*]", "]");
        
        // 问题 3: 确保对象/数组正确闭合
        int openBraces = fixed.length() - fixed.replace("{", "").length();
        int closeBraces = fixed.length() - fixed.replace("}", "").length();
        if (openBraces > closeBraces) {
            fixed = fixed + "}".repeat(openBraces - closeBraces);
            log.debug("补充了 {} 个缺失的闭合花括号", openBraces - closeBraces);
        }
        
        int openBrackets = fixed.length() - fixed.replace("[", "").length();
        int closeBrackets = fixed.length() - fixed.replace("]", "").length();
        if (openBrackets > closeBrackets) {
            fixed = fixed + "]".repeat(openBrackets - closeBrackets);
            log.debug("补充了 {} 个缺失的闭合方括号", openBrackets - closeBrackets);
        }
        
        if (!fixed.equals(jsonContent)) {
            log.debug("JSON 修复完成 - 原始长度:{}, 修复后长度:{}", jsonContent.length(), fixed.length());
        }
        
        return fixed;
    }
    
    /**
     * 步骤 5: 合并每个工作表的数据（按主键分组，聚合合并）
     */
    private static Map<String, List<Object[]>> mergeSegmentData(
            Map<Integer, Map<Integer, String>> resultFileMap,
            Map<String, Object> headerInfo,
            Long userId, FileService fileService) throws Exception {
        log.info("步骤 5: 合并段落数据（按主键分组，聚合合并）");
        
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");
        Map<String, List<Object[]>> mergedData = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        
        for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
            Map<String, Object> sheetInfo = sheets.get(sheetIndex);
            String sheetName = (String) sheetInfo.get("sheetName");
            List<String> headers = (List<String>) sheetInfo.get("headers");
            int colCount = headers.size();
            
            Map<Integer, String> segFileMap = resultFileMap.getOrDefault(sheetIndex, new HashMap<>());
            List<Integer> sortedSegIds = new ArrayList<>(segFileMap.keySet());
            Collections.sort(sortedSegIds);
            
            // 第一步：收集所有段落的所有有效行（主键非空），并按段落顺序加入 allRows
            List<Object[]> allRows = new ArrayList<>();
            int primaryKeyCol = -1;
            
            for (int segId : sortedSegIds) {
                String fileId = segFileMap.get(segId);
                if (fileId == null) continue;
                
                byte[] fileContent = fileService.getFileContent(fileId, userId);
                if (fileContent == null) continue;
                
                SegmentResult result = mapper.readValue(fileContent, SegmentResult.class);
                
                // 确定主键列索引
                if (primaryKeyCol == -1) {
                    primaryKeyCol = result.getPrimaryKeyColumn();
                } else if (result.getPrimaryKeyColumn() != primaryKeyCol) {
                    log.warn("工作表 {} 段落{} 的主键列索引 ({}) 与之前的不一致 ({})，将跳过该段落",
                             sheetName, segId, result.getPrimaryKeyColumn(), primaryKeyCol);
                    continue;
                }
                
                List<Object[]> rows = extractRowsFromSegment(result, colCount);
                for (Object[] row : rows) {
                    if (row.length <= primaryKeyCol) {
                        log.warn("段落{}：行数据列数不足，无法获取主键", segId);
                        continue;
                    }
                    Object keyObj = row[primaryKeyCol];
                    if (keyObj == null) continue;
                    
                    // 过滤全空行
                    if (isAllEmpty(row)) {
                        log.debug("段落{}：忽略全空行", segId);
                        continue;
                    }
                    
                    allRows.add(row);
                }
            }
            
            if (primaryKeyCol == -1) {
                log.warn("工作表 {} 未找到任何有效段落，将返回空列表", sheetName);
                mergedData.put(sheetName, new ArrayList<>());
                continue;
            }
            
            // 第二步：按主键分组所有行（保留在 allRows 中的顺序）
            Map<Object, List<Object[]>> rowsByKey = new LinkedHashMap<>();
            for (Object[] row : allRows) {
                Object key = row[primaryKeyCol];
                rowsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
            
            // 第三步：对每个主键组内的所有行进行聚合合并（取非空值并集）
            Map<Object, Object[]> mergedByKey = new LinkedHashMap<>();
            for (Map.Entry<Object, List<Object[]>> entry : rowsByKey.entrySet()) {
                Object key = entry.getKey();
                List<Object[]> rows = entry.getValue();
                
                // 先去重（避免完全相同的重复行）
                List<Object[]> uniqueRows = new ArrayList<>();
                for (Object[] row : rows) {
                    boolean duplicate = false;
                    for (Object[] existing : uniqueRows) {
                        if (Arrays.equals(row, existing)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        uniqueRows.add(row);
                    }
                }
                
                // 聚合合并：取每个单元格的第一个非空值（保留顺序）
                Object[] merged = new Object[colCount];
                Arrays.fill(merged, null);
                for (Object[] row : uniqueRows) {
                    for (int i = 0; i < colCount; i++) {
                        Object val = row[i];
                        if (val != null && !isMissingValue(val)) {
                            if (merged[i] == null) {
                                merged[i] = val;
                            } else {
                                // 冲突：已有值，新值不同
                                if (!Objects.equals(merged[i], val)) {
                                    log.warn("主键 {} 列 {} 存在冲突值：'{}' vs '{}'，将保留第一个值 '{}'",
                                             key, i, merged[i], val, merged[i]);
                                }
                            }
                        }
                    }
                }
                mergedByKey.put(key, merged);
            }
            
            List<Object[]> finalRows = new ArrayList<>(mergedByKey.values());
            mergedData.put(sheetName, finalRows);
            log.info("工作表 '{}' 合并后共 {} 行（原始有效行 {} 行）",
                     sheetName, finalRows.size(), allRows.size());
        }
        
        return mergedData;
    }
    
    /**
     * 从段落结果中提取行数据（优先使用 AI 返回的"总行数"）
     */
    private static List<Object[]> extractRowsFromSegment(SegmentResult result, int colCount) {
        List<Object[]> rows = new ArrayList<>();
        Map<String, Object> cellData = result.getCellData();
        
        if (cellData == null || cellData.isEmpty()) {
            return rows;
        }
        
        // 优先使用 AI 返回的"总行数"，避免生成多余空行
        int totalRows = result.getTotalRows();
        int maxRowFromCells = -1;
        
        // 扫描所有单元格位置，找到最大行号
        for (String cellPos : cellData.keySet()) {
            if (cellPos.matches("R\\d+C\\d+")) {
                int row = Integer.parseInt(cellPos.substring(1, cellPos.indexOf('C')));
                maxRowFromCells = Math.max(maxRowFromCells, row);
            }
        }
        
        // 取 AI 返回的总行数和实际单元格最大行号的较大值
        // 这样可以确保不会遗漏数据，同时避免生成过多空行
        int maxRow = Math.max(totalRows - 1, maxRowFromCells);
        if (maxRow < 0) {
            maxRow = maxRowFromCells;
        }
        
        log.debug("AI 返回总行数={}, 单元格最大行号={}, 最终使用 maxRow={}", 
                 totalRows, maxRowFromCells, maxRow);
        
        for (int row = 0; row <= maxRow; row++) {
            Object[] rowData = new Object[colCount];
            Arrays.fill(rowData, null);
            
            for (int col = 0; col < colCount; col++) {
                String cellPos = "R" + row + "C" + col;
                if (cellData.containsKey(cellPos)) {
                    rowData[col] = cellData.get(cellPos);
                }
            }
            
            rows.add(rowData);
        }
        
        return rows;
    }
    
    /**
     * 判断两行是否完全相同
     */
    private static boolean isCompleteDuplicate(Object[] row1, Object[] row2) {
        if (row1.length != row2.length) {
            return false;
        }
        
        for (int i = 0; i < row1.length; i++) {
            if (!Objects.equals(row1[i], row2[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 判断行是否全为空（包括"数据丢失"标记）
     */
    private static boolean isAllEmpty(Object[] row) {
        for (Object cell : row) {
            if (cell != null) {
                String str = cell.toString().trim();
                // 如果字符串非空且不等于"数据丢失"，则行非空
                if (!str.isEmpty() && !MISSING_VALUE_MARKER.equals(str)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * 判断是否为丢失值（null、空字符串或"数据丢失"标记）
     */
    private static boolean isMissingValue(Object val) {
        if (val == null) return true;
        String str = val.toString().trim();
        return str.isEmpty() || MISSING_VALUE_MARKER.equals(str);
    }
    
    /**
     * 判断两行是否可以合并（符合附录 A 规则）
     * 合并条件：上一段最后一行后半部分为空 且 当前段第一行前半部分为空
     */
    private static boolean canMergeRows(Object[] lastRow, Object[] firstRow, int colCount) {
        int midPoint = colCount / 2;
        
        // 检查上一行后半部分是否为空
        boolean lastRowBackHalfEmpty = true;
        for (int i = midPoint; i < colCount; i++) {
            if (lastRow[i] != null && !isMissingValue(lastRow[i])) {
                lastRowBackHalfEmpty = false;
                break;
            }
        }
        
        // 如果后半部分不空，直接返回 false（不需要再检查前半部分）
        if (!lastRowBackHalfEmpty) {
            return false;
        }
        
        // 检查当前行前半部分是否为空
        boolean firstRowFrontHalfEmpty = true;
        for (int i = 0; i < midPoint; i++) {
            if (firstRow[i] != null && !isMissingValue(firstRow[i])) {
                firstRowFrontHalfEmpty = false;
                break;
            }
        }
        
        // 只有当上一行后半空 且 当前行前半空时，才允许合并
        return firstRowFrontHalfEmpty;
    }
    
    /**
     * 合并两行数据（取非空值，若都非空且相等则取该值）
     */
    private static Object[] mergeTwoRows(Object[] lastRow, Object[] firstRow, int colCount) {
        Object[] merged = new Object[colCount];
        
        for (int i = 0; i < colCount; i++) {
            Object lastVal = lastRow[i];
            Object firstVal = firstRow[i];
            
            // 优先取非空且非"数据丢失"的值
            if (lastVal != null && !isMissingValue(lastVal)) {
                merged[i] = lastVal;
            } else if (firstVal != null && !isMissingValue(firstVal)) {
                merged[i] = firstVal;
            } else {
                merged[i] = null; // 两者都空或都为丢失标记
            }
        }
        
        return merged;
    }
    
    /**
     * 步骤 7: 清理与返回
     */
    private static void cleanupAndFinish(String filledFileId, String templateFileId,
                                         Long userId, FileService fileService) throws Exception {
        log.info("步骤 7: 清理临时文件并移动结果");
        
        File filledFile = fileService.getFileById(filledFileId, userId);
        if (filledFile != null) {
            fileService.moveFileToSection(filledFileId, "result", userId);
            log.info("结果文件已移动到 result 区域");
        }
        
        try {
            String headerFileName = templateFileId + "_headers.json";
            deleteFileByName(headerFileName, "temp", userId, fileService);
            log.info("表头信息文件已删除：{}", headerFileName);
        } catch (Exception e) {
            log.warn("删除表头信息文件失败：{}", e.getMessage());
        }
        
        try {
            deleteAIResultFiles(templateFileId, "temp", userId, fileService);
            log.info("AI 结果临时文件已清理");
        } catch (Exception e) {
            log.warn("清理 AI 结果文件失败：{}", e.getMessage());
        }
    }
    
    /**
     * 批量删除 AI 结果临时文件
     */
    private static void deleteAIResultFiles(String templateFileId, String section, 
                                            Long userId, FileService fileService) throws Exception {
        List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, section);
        String aiResultPrefix = templateFileId + AI_RESULT_FILE_PREFIX;
        
        for (File file : tempFiles) {
            if (file.getOriginalName() != null && 
                file.getOriginalName().startsWith(aiResultPrefix) &&
                file.getOriginalName().endsWith(".json")) {
                fileService.deleteFileById(file.getId(), userId);
                log.debug("已删除 AI 结果文件：{}", file.getOriginalName());
            }
        }
    }
    
    /**
     * 根据文件名删除文件（用于清理临时文件）
     */
    private static void deleteFileByName(String fileName, String section, 
                                         Long userId, FileService fileService) throws Exception {
        List<File> files = fileService.getFilesByUserIdAndSection(userId, section);
        for (File file : files) {
            if (file.getFileName().equals(fileName) || file.getOriginalName().equals(fileName)) {
                fileService.deleteFileById(file.getId(), userId);
                log.debug("已删除文件：{}", fileName);
                break;
            }
        }
    }
    
    /**
     * 文本段落类
     */
    @lombok.Data
    static class TextSegment {
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
    
    /**
     * 段落提取结果类
     */
    @lombok.Data
    static class SegmentResult {
        private int segmentId;
        private int totalRows;
        private int primaryKeyColumn; // 主键列索引（从 0 开始）
        private Map<String, Object> cellData; // 格式：RxCy -> value
        
        public int getSegmentId() { return segmentId; }
        public void setSegmentId(int segmentId) { this.segmentId = segmentId; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getPrimaryKeyColumn() { return primaryKeyColumn; }
        public void setPrimaryKeyColumn(int primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }
        public Map<String, Object> getCellData() { return cellData; }
        public void setCellData(Map<String, Object> cellData) { this.cellData = cellData; }
    }
}