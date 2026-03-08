package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文本到 Word 的智能填表工具类（静态版本）
 */
public class TxtToWord {
    
    private static final Logger log = LoggerFactory.getLogger(TxtToWord.class);

    private static final int CHUNK_SIZE = 30 * 1024; // 30KB
    private static final int OVERLAP_SIZE = 100; // 重叠字符数
    private static final int MAX_CONCURRENT_AI_CALLS = 10;
    private static final String AI_RESULT_FILE_PREFIX = "_ai_result_table";
    // 数据丢失标记常量
    private static final String MISSING_VALUE_MARKER = "<null>";

    /**
     * 处理文本到 Word 的填表操作
     * @param readFile 读取区的文件（纯文本文件）
     * @param templateFile 模板区的 Word 文件
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param userConfigService 用户配置服务（用于获取 API Key）
     * @param apiService API 服务（用于调用 AI 接口）
     * @return 处理结果描述
     */
    public static String process(File readFile, File templateFile, Long userId,
                                FileService fileService, UserConfigService userConfigService, ApiService apiService) {
        // 用于记录所有已创建的临时文件 ID
        Set<String> tempFileIds = new HashSet<>();
        String filledFileId = null;
        Map<Integer, Map<Integer, String>> resultFileMap = null;
        boolean success = false;
        
        try {
            log.info("========== 开始执行文本到 Word 填表处理 ==========");
            log.info("读取文件：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
            log.info("模板文件：{} (ID: {})", templateFile.getOriginalName(), templateFile.getId());
            log.info("用户 ID: {}", userId);
            
            // 步骤 0: 提取模板表格信息并保存为 JSON
            log.info("【步骤 0】开始提取模板表格信息...");
            Map<String, Object> tableInfo = WordTableExtractorUtil.extractTables(templateFile, userId, fileService);
            if (tableInfo == null) {
                log.error("【步骤 0】提取模板表格信息失败");
                return "提取模板表格信息失败";
            }
            log.info("【步骤 0】表格信息提取成功，表格数量：{}", tableInfo.get("tableNum"));
            // 将表格文件的真实 ID 加入临时集合
            String tableInfoFileId = (String) tableInfo.get("fileId");
            tempFileIds.add(tableInfoFileId);
            log.info("【步骤 0】表格信息文件 ID: {}", tableInfoFileId);
            
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
            List<TextChunk> chunks = segmentText(fullText);
            log.info("【步骤 3】文本已切分为 {} 个段落", chunks.size());
            
            // 步骤 4: 并行 AI 提取所有段数据（任一失败则抛出异常）
            log.info("【步骤 4】开始并行 AI 提取数据...");
            resultFileMap = extractDataWithAI(
                chunks, tableInfo, userId, templateFile.getId(), fileService, userConfigService, apiService);
            log.info("【步骤 4】AI 数据提取完成，成功处理 {} 个表格", resultFileMap.size());
            
            // 将 AI 结果文件 ID 加入临时集合
            for (Map<Integer, String> chunkMap : resultFileMap.values()) {
                tempFileIds.addAll(chunkMap.values());
            }
            log.info("【步骤 4】已将 {} 个 AI 结果文件加入清理列表", tempFileIds.size() - 2);
            
            // 步骤 5: 合并每个表格的数据（从临时文件加载数据）
            log.info("【步骤 5】开始合并段落数据...");
            Map<Integer, List<Object[]>> mergedTableData = mergeChunkData(
                resultFileMap, tableInfo, userId, fileService);
            log.info("【步骤 5】数据合并完成，共 {} 个表格", mergedTableData.size());
            
            // 步骤 6: 串行写入每个表格的数据到 Word 副本
            log.info("【步骤 6】开始填充 Word 模板...");
            String updatedFileId = WordSplicerUtil.fillWordTemplate(filledFileId, mergedTableData, tableInfo, userId, fileService, templateFile.getOriginalName());
            log.info("【步骤 6】Word 填充完成，新文件 ID: {}", updatedFileId);
            
            // 更新临时文件记录：旧文件已被删除，新文件将移动到 result 区
            tempFileIds.remove(filledFileId);
            tempFileIds.add(updatedFileId);
            filledFileId = updatedFileId;
            
            // 步骤 7: 清理与返回（使用更新后的文件 ID）
            log.info("【步骤 7】开始清理临时文件并移动结果...");
            cleanupAndFinish(updatedFileId, tempFileIds, userId, fileService);
            
            // 标记成功，避免 finally 重复清理
            success = true;
            
            log.info("========== 文本到 Word 填表处理完成 ==========");
            return "已完成智能填写操作";
            
        } catch (Exception e) {
            log.error("========== 文本到 Word 填表处理失败 ==========", e);
            log.error("错误信息：{}", e.getMessage());
            log.error("错误类型：{}", e.getClass().getSimpleName());
            return "文本到 Word 填表处理失败：" + e.getMessage();
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
        log.info("步骤 2: 创建 Word 模板副本");
        
        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) {
            return null;
        }
        
        MultipartFile copyFile = new MultipartFile() {
            @Override
            public String getName() {
                return templateFile.getId() + "_filled.docx";
            }

            @Override
            public String getOriginalFilename() {
                return templateFile.getId() + "_filled.docx";
            }

            @Override
            public String getContentType() {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
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
        log.info("Word 模板副本已创建：{}", savedFile.getId());
        
        return savedFile.getId();
    }
    
    /**
     * 根据 fileName 查找对应的文件
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
     */
    private static List<TextChunk> segmentText(String fullText) {
        log.info("步骤 3: 文本切割");
        
        List<TextChunk> chunks = new ArrayList<>();
        int nextStart = 0;
        int chunkIndex = 0;
        
        while (nextStart < fullText.length()) {
            int chunkStart = nextStart;
            int chunkEnd = Math.min(nextStart + CHUNK_SIZE, fullText.length());
            
            // 如果不是第一段，向前包含重叠部分
            if (chunkIndex > 0) {
                chunkStart = Math.max(0, chunkStart - OVERLAP_SIZE);
            }
            
            String content = fullText.substring(chunkStart, chunkEnd);
            
            TextChunk chunk = new TextChunk();
            chunk.setChunkId(chunkIndex);
            chunk.setContent(content);
            chunk.setStartPos(chunkStart);
            chunk.setEndPos(chunkEnd);
            
            chunks.add(chunk);
            log.debug("创建段落 {}: 起始={}, 结束={}, 长度={}", 
                     chunkIndex, chunkStart, chunkEnd, content.length());
            
            nextStart = chunkEnd;
            chunkIndex++;
        }
        
        return chunks;
    }
    
    /**
     * 步骤 4: 并行 AI 提取所有段数据
     */
    private static Map<Integer, Map<Integer, String>> extractDataWithAI(
            List<TextChunk> chunks, Map<String, Object> tableInfo,
            Long userId, String originalTemplateFileId, 
            FileService fileService, UserConfigService userConfigService, ApiService apiService) throws Exception {
        log.info("步骤 4: 并行 AI 提取数据");
        
        // 提前获取用户配置，避免每个子任务重复查询数据库
        String apiKey = getApiKeyFromUserConfig(userId, userConfigService);
        String apiUrl = getApiUrlFromUserConfig(userId, userConfigService);
        String modelName = getAnalysisModelNameFromUserConfig(userId, userConfigService);
        
        // 检查配置是否有效
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("未配置 API Key，请先在用户配置中设置 SiliconFlow API Key");
        }
        
        List<Map<String, Object>> tables = (List<Map<String, Object>>) tableInfo.get("tables");
        Map<Integer, Map<Integer, String>> resultFileMap = new ConcurrentHashMap<>();
        
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicReference<Exception> failureException = new AtomicReference<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_AI_CALLS);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_AI_CALLS);
        long timeoutMinutes = Long.parseLong(System.getProperty("ai.extract.timeout.minutes", "10"));
        CountDownLatch latch = new CountDownLatch(chunks.size() * tables.size());
        
        try {
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                TextChunk chunk = chunks.get(chunkIndex);
                for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                    final int chunkIdx = chunkIndex;
                    final int tableIdx = tableIndex;
                    
                    executor.submit(() -> {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            hasFailure.set(true);
                            failureException.set(e);
                            latch.countDown();
                            return;
                        }
                        
                        try {
                            ChunkResult result = callAIForChunk(
                                chunk, 
                                (Map<String, Object>) tables.get(tableIdx),
                                userId,
                                apiKey,
                                apiUrl,
                                modelName,
                                apiService
                            );
                            
                            if (result == null || result.getCellData() == null || result.getCellData().isEmpty()) {
                                log.warn("段落{}，表格{}：AI 返回的数据为空或无效", chunkIdx, tableIdx);
                            } else {
                                String fileId = saveAIResultToTemp(result, chunkIdx, tableIdx, originalTemplateFileId, userId, fileService);
                                resultFileMap.computeIfAbsent(tableIdx, k -> new ConcurrentHashMap<>())
                                            .put(chunkIdx, fileId);
                                log.info("AI 提取完成 - 段落{}，表格{}，结果文件：{}, 单元格数：{}", 
                                        chunkIdx, tableIdx, fileId, result.getCellData().size());
                            }
                        } catch (Exception e) {
                            log.error("AI 提取失败 - 段落{}，表格{}: {}", chunkIdx, tableIdx, e.getMessage(), e);
                            hasFailure.set(true);
                            failureException.set(e);
                        } finally {
                            semaphore.release();
                            latch.countDown();
                        }
                    });
                }
            }
            
            if (!latch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                log.error("AI 数据提取超时 ({}分钟)", timeoutMinutes);
                throw new TimeoutException("AI 数据提取超时 (" + timeoutMinutes + "分钟)");
            }
            
            if (hasFailure.get()) {
                throw failureException.get();
            }
            
            int totalTasks = chunks.size() * tables.size();
            int successfulTasks = resultFileMap.values().stream().mapToInt(Map::size).sum();
            log.info("AI 数据提取全部成功（共{}个任务）", successfulTasks);
            
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
        
        return resultFileMap;
    }
    
    /**
     * 保存 AI 提取结果到临时文件
     */
    private static String saveAIResultToTemp(ChunkResult result, int chunkId, int tableId,
                                      String templateFileId, Long userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(result);
        String fileName = templateFileId + AI_RESULT_FILE_PREFIX + tableId + "_chunk" + chunkId + ".json";
        MultipartFile aiResultFile = JsonToExcelWriterUtil.createMultipartFile(
            fileName, fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8)
        );
        File savedFile = fileService.saveFile(aiResultFile, "temp", userId);
        log.debug("AI 结果已持久化到临时文件：{} (ID: {})", fileName, savedFile.getId());
        return savedFile.getId();
    }
    
    /**
     * 调用 AI 接口提取单个段落的数据
     */
    private static ChunkResult callAIForChunk(TextChunk chunk, Map<String, Object> tableInfo,
                                              Long userId, String apiKey, String apiUrl, String modelName, ApiService apiService) throws Exception {
        String description1 = (String) tableInfo.get("sheetDiscription1");
        String description2 = (String) tableInfo.get("sheetDiscription2");
        List<String> headers = (List<String>) tableInfo.get("headers");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请从以下文本中提取表格数据。\n");
        prompt.append("表格上下文：\n");
        if (!description1.isEmpty()) {
            prompt.append("上文：").append(description1).append("\n");
        }
        if (!description2.isEmpty()) {
            prompt.append("下文：").append(description2).append("\n");
        }
        prompt.append("表头为：").append(String.join(", ", headers));
        prompt.append("\n\n文本内容编号：").append(chunk.getChunkId());
        prompt.append("\n\n文本内容:\n").append(chunk.getContent());
        prompt.append("\n\n请严格按照以下 JSON 格式返回结果:\n");
        prompt.append("{\n");
        prompt.append("  \"块编号\": ").append(chunk.getChunkId()).append(",\n");
        prompt.append("  \"行数\": <数字>,\n");
        prompt.append("  \"主键列名\": \"主键列的表头名称\",\n");
        prompt.append("  \"表格内容\": {\n");
        prompt.append("    \"R0C0\": \"单元格内容\",\n");
        prompt.append("    \"R0C1\": \"单元格内容\",\n");
        prompt.append("    ...\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("重要要求：\n");
        prompt.append("1. 必须直接返回纯 JSON 数据，不要有任何其他文字、说明或标记\n");
        prompt.append("2. 禁止使用 Markdown 代码块（不要用 ```json 包裹）\n");
        prompt.append("3. 单元格位置用 RxCy 表示，x是行号，y是列号\n");
        prompt.append("4. 如果某个单元格没有数据，填<null>\n");
        prompt.append("5. 表格内容中不要出现表头，仅包含单元格数据\n");
        prompt.append("6. 确保 JSON 格式完整且可解析\n");
        prompt.append("7. 必须返回完整的 JSON，不得在中间截断\n");
        prompt.append("8. 所有键名和字符串值必须使用英文双引号 \" 包裹\n");
        prompt.append("9. 确保所有中文字符正确编码，不出现乱码\n");
        prompt.append("10. 如果数据量较大，请确保完整输出，即使超过常规长度限制\n");
        
        String aiResponse = apiService.callExternalApi(apiUrl, apiKey, prompt.toString(), modelName);
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            throw new Exception("AI 服务返回空响应 - 段落" + chunk.getChunkId());
        }
        
        return parseAIResponse(aiResponse, chunk.getChunkId());
    }
    
    private static String getApiKeyFromUserConfig(Long userId, UserConfigService userConfigService) {
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowApiKey() == null ||
                userConfig.getSiliconFlowApiKey().isEmpty()) {
            log.warn("用户 {} 未配置 API Key", userId);
            return null;
        }
        return userConfig.getSiliconFlowApiKey();
    }
    
    private static String getApiUrlFromUserConfig(Long userId, UserConfigService userConfigService) {
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowBaseUrl() == null ||
                userConfig.getSiliconFlowBaseUrl().isEmpty()) {
            log.warn("用户 {} 未配置 API URL，使用默认值：https://api.siliconflow.com/v1", userId);
            return "https://api.siliconflow.com/v1";
        }
        return userConfig.getSiliconFlowBaseUrl();
    }

    private static String getAnalysisModelNameFromUserConfig(Long userId, UserConfigService userConfigService) {
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getAnalysisModelName() == null ||
                userConfig.getAnalysisModelName().isEmpty()) {
            log.warn("用户 {} 未配置 analysisModelName，使用默认值：deepseek-ai/DeepSeek-V3.2", userId);
            return "deepseek-ai/DeepSeek-V3.2";
        }
        return userConfig.getAnalysisModelName();
    }
    
    /**
     * 解析 AI 响应的 JSON 数据
     */
    private static ChunkResult parseAIResponse(String aiResponse, int chunkId) throws Exception {
        ChunkResult result = new ChunkResult();
        result.setChunkId(chunkId);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            log.info("AI 原始响应前 500 字符：{}", 
                aiResponse.length() > 500 ? aiResponse.substring(0, 500) + "..." : aiResponse);
            
            String jsonContent = extractJsonFromResponse(aiResponse);
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                log.error("无法提取 JSON - AI 响应内容：{}", aiResponse);
                throw new Exception("无法从 AI 响应中提取有效 JSON");
            }
            
            log.debug("提取到的 JSON 内容：{}", jsonContent);
            jsonContent = fixCommonJsonIssues(jsonContent);
            
            JsonNode rootNode = mapper.readTree(jsonContent);
            
            if (rootNode.has("块编号")) {
                result.setChunkId(rootNode.get("块编号").asInt(chunkId));
            } else {
                result.setChunkId(chunkId);
            }
            
            int rowCount = rootNode.has("行数") ? rootNode.get("行数").asInt() : 0;
            result.setRowCount(rowCount);
            
            String primaryKeyCol = rootNode.has("主键列名") ? rootNode.get("主键列名").asText() : null;
            result.setPrimaryKeyColumn(primaryKeyCol);
            
            Map<String, Object> cellData = new HashMap<>();
            JsonNode tableNode = rootNode.get("表格内容");
            
            if (tableNode != null && tableNode.isObject()) {
                tableNode.fields().forEachRemaining(entry -> {
                    String cellPos = entry.getKey();
                    if (!cellPos.matches("R\\d+C\\d+")) {
                        log.warn("无效的单元格位置：{}，已忽略", cellPos);
                        return;
                    }
                    
                    JsonNode valueNode = entry.getValue();
                    if (valueNode.isNull()) {
                        return;
                    }
                    
                    if (valueNode.isTextual()) {
                        String text = valueNode.asText();
                        if (MISSING_VALUE_MARKER.equals(text)) {
                            return;
                        }
                        cellData.put(cellPos, text);
                    } else if (valueNode.isNumber()) {
                        cellData.put(cellPos, valueNode.numberValue());
                    } else if (valueNode.isBoolean()) {
                        cellData.put(cellPos, valueNode.booleanValue());
                    } else {
                        cellData.put(cellPos, valueNode.toString());
                    }
                });
            } else {
                log.warn("AI 响应缺少'表格内容'字段或格式不正确，使用空数据");
            }
            
            result.setCellData(cellData);
            log.info("解析 AI 响应 - 段落{}，行数{}，单元格数{}", chunkId, rowCount, cellData.size());
            
        } catch (Exception e) {
            log.error("解析 AI 响应失败 - 段落{}，原始响应：{}", chunkId, aiResponse, e);
            throw new Exception("解析 AI 响应失败 - 段落" + chunkId + ": " + e.getMessage(), e);
        }
        
        return result;
    }
    
    private static String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = response.trim();
        
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        String markdownCleaned = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        markdownCleaned = markdownCleaned.trim();
        
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        int startIdx = trimmed.indexOf('{');
        int endIdx = trimmed.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            String jsonCandidate = trimmed.substring(startIdx, endIdx + 1);
            log.debug("从原始响应中提取到 JSON 候选：{} (长度:{})", 
                    jsonCandidate.length() > 100 ? jsonCandidate.substring(0, 100) + "..." : jsonCandidate, 
                    jsonCandidate.length());
            return jsonCandidate;
        }
        
        startIdx = markdownCleaned.indexOf('{');
        endIdx = markdownCleaned.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            String jsonCandidate = trimmed.substring(startIdx, endIdx + 1);
            log.debug("从清理后响应中提取到 JSON 候选：{} (长度:{})", 
                    jsonCandidate.length() > 100 ? jsonCandidate.substring(0, 100) + "..." : jsonCandidate, 
                    jsonCandidate.length());
            return jsonCandidate;
        }
        
        log.warn("检测到 JSON 可能被截断，尝试修复...");
        startIdx = trimmed.indexOf('{');
        if (startIdx >= 0) {
            int lastComma = trimmed.lastIndexOf(',');
            if (lastComma > startIdx) {
                return trimmed.substring(startIdx, lastComma) + "}";
            }
        }
        
        log.warn("无法从响应中提取 JSON - 原始响应前 200 字符：{}", 
                trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
        
        return null;
    }
    
    private static String fixCommonJsonIssues(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return jsonContent;
        }
        
        String fixed = jsonContent.trim();
        
        fixed = fixed.replaceAll("([{,])\\s*\"", "$1\"");
        fixed = fixed.replaceAll("\"\\s*:", "\":");
        
        fixed = fixed.replaceAll(",\\s*}", "}");
        fixed = fixed.replaceAll(",\\s*]", "]");
        
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
     * 步骤 5: 合并每个表格的数据
     */
    private static Map<Integer, List<Object[]>> mergeChunkData(
            Map<Integer, Map<Integer, String>> resultFileMap,
            Map<String, Object> tableInfo,
            Long userId, FileService fileService) throws Exception {
        log.info("步骤 5: 合并段落数据（按主键分组，聚合合并）");
        
        List<Map<String, Object>> tables = (List<Map<String, Object>>) tableInfo.get("tables");
        Map<Integer, List<Object[]>> mergedData = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            Map<String, Object> tableInfoItem = tables.get(tableIndex);
            List<String> headers = (List<String>) tableInfoItem.get("headers");
            int colCount = headers.size();
            String primaryKeyHeader = (String) tableInfoItem.get("primaryKeyHeader");
            
            Map<Integer, String> chunkFileMap = resultFileMap.getOrDefault(tableIndex, new HashMap<>());
            List<Integer> sortedChunkIds = new ArrayList<>(chunkFileMap.keySet());
            Collections.sort(sortedChunkIds);
            
            List<Object[]> allRows = new ArrayList<>();
            Integer primaryKeyColIndex = null;
            
            if (primaryKeyHeader != null) {
                for (int i = 0; i < headers.size(); i++) {
                    if (headers.get(i).equals(primaryKeyHeader)) {
                        primaryKeyColIndex = i;
                        break;
                    }
                }
                log.info("表格 {} 使用模板主键列：'{}'，索引：{}", tableIndex, primaryKeyHeader, primaryKeyColIndex);
            }
            
            for (int chunkId : sortedChunkIds) {
                String fileId = chunkFileMap.get(chunkId);
                if (fileId == null) continue;
                
                byte[] fileContent = fileService.getFileContent(fileId, userId);
                if (fileContent == null) continue;
                
                ChunkResult result = mapper.readValue(fileContent, ChunkResult.class);
                
                if (primaryKeyColIndex == null && result.getPrimaryKeyColumn() != null) {
                    for (int i = 0; i < headers.size(); i++) {
                        if (headers.get(i).equals(result.getPrimaryKeyColumn())) {
                            primaryKeyColIndex = i;
                            break;
                        }
                    }
                    if (primaryKeyColIndex != null) {
                        log.info("表格 {} 使用 AI 返回主键列：'{}'，索引：{}", tableIndex, result.getPrimaryKeyColumn(), primaryKeyColIndex);
                    }
                }
                
                List<Object[]> rows = extractRowsFromChunk(result, colCount);
                for (Object[] row : rows) {
                    if (primaryKeyColIndex != null && (primaryKeyColIndex >= row.length || row[primaryKeyColIndex] == null)) {
                        log.debug("段落{}：跳过主键为空的行", chunkId);
                        continue;
                    }
                    if (isAllEmpty(row)) {
                        log.debug("段落{}：忽略全空行", chunkId);
                        continue;
                    }
                    allRows.add(row);
                }
            }
            
            if (headers.isEmpty()) {
                log.warn("表格 {} 未找到任何有效段落，将返回空列表", tableIndex);
                mergedData.put(tableIndex, new ArrayList<>());
                continue;
            }
            
            if (primaryKeyColIndex != null) {
                Map<Object, List<Object[]>> rowsByKey = new LinkedHashMap<>();
                for (Object[] row : allRows) {
                    Object key = row[primaryKeyColIndex];
                    rowsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                }
                
                Map<Object, Object[]> mergedByKey = new LinkedHashMap<>();
                for (Map.Entry<Object, List<Object[]>> entry : rowsByKey.entrySet()) {
                    Object key = entry.getKey();
                    List<Object[]> rows = entry.getValue();
                    
                    List<Object[]> uniqueRows = new ArrayList<>();
                    for (Object[] row : rows) {
                        boolean duplicate = false;
                        for (Object[] existing : uniqueRows) {
                            if (rowsEqual(row, existing)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            uniqueRows.add(row);
                        }
                    }
                    
                    Object[] merged = new Object[colCount];
                    Arrays.fill(merged, null);
                    for (Object[] row : uniqueRows) {
                        for (int i = 0; i < colCount; i++) {
                            Object val = row[i];
                            if (val != null && !isMissingValue(val)) {
                                if (merged[i] == null) {
                                    merged[i] = val;
                                } else if (!Objects.equals(merged[i], val)) {
                                    log.warn("主键 {} 列 {} 存在冲突值：'{}' vs '{}'，将保留第一个值 '{}'",
                                             key, i, merged[i], val, merged[i]);
                                }
                            }
                        }
                    }
                    mergedByKey.put(key, merged);
                }
                mergedData.put(tableIndex, new ArrayList<>(mergedByKey.values()));
                log.info("表格 '{}' 合并后共 {} 行（原始有效行 {} 行）",
                         tableIndex, mergedByKey.size(), allRows.size());
            } else {
                // 无主键：按行内容去重（使用自定义比较方法）
                List<Object[]> deduplicated = new ArrayList<>();
                for (Object[] row : allRows) {
                    boolean duplicate = false;
                    for (Object[] existing : deduplicated) {
                        if (rowsEqual(row, existing)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        deduplicated.add(row);
                    }
                }
                mergedData.put(tableIndex, deduplicated);
                log.info("表格 '{}' 无主键，简单去重后共 {} 行（原始有效行 {} 行）", 
                         tableIndex, deduplicated.size(), allRows.size());
            }
        }
        return mergedData;
    }
    
    private static List<Object[]> extractRowsFromChunk(ChunkResult result, int colCount) {
        List<Object[]> rows = new ArrayList<>();
        Map<String, Object> cellData = result.getCellData();
        if (cellData == null || cellData.isEmpty()) {
            return rows;
        }
        
        // 计算单元格中的最大行号
        int maxRowFromCells = -1;
        for (String cellPos : cellData.keySet()) {
            if (cellPos.matches("R\\d+C\\d+")) {
                int row = Integer.parseInt(cellPos.substring(1, cellPos.indexOf('C')));
                maxRowFromCells = Math.max(maxRowFromCells, row);
            }
        }
        
        // 确定总行数：优先信任 AI 返回的 rowCount，但确保能覆盖所有有数据的行
        int totalRows;
        if (result.getRowCount() > 0) {
            totalRows = result.getRowCount();
            if (totalRows - 1 < maxRowFromCells) {
                log.warn("段落 {}：AI 返回的行数 {} 小于实际单元格最大行号 {}，将使用 {} 作为总行数",
                         result.getChunkId(), result.getRowCount(), maxRowFromCells + 1, maxRowFromCells + 1);
                totalRows = maxRowFromCells + 1;
            }
        } else {
            totalRows = maxRowFromCells + 1;
            if (totalRows <= 0) {
                totalRows = 0;
            }
        }
        
        log.debug("段落 {}: AI 返回行数={}, 单元格最大行号={}, 最终使用总行数={}", 
                 result.getChunkId(), result.getRowCount(), maxRowFromCells, totalRows);
        
        // 生成每一行的数据
        for (int row = 0; row < totalRows; row++) {
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
     * 步骤 7: 清理与返回
     */
    private static void cleanupAndFinish(String resultFileId, Set<String> tempFileIds, Long userId, FileService fileService) throws Exception {
        log.info("步骤 7: 清理临时文件并移动结果");
        
        File filledFile = fileService.getFileById(resultFileId, userId);
        if (filledFile != null) {
            fileService.moveFileToSection(resultFileId, "result", userId);
            log.info("结果文件已移动到 result 区域");
        }
        
        for (String fileId : tempFileIds) {
            if (!fileId.equals(resultFileId)) {
                try {
                    fileService.deleteFileById(fileId, userId);
                    log.debug("已删除临时文件：{}", fileId);
                } catch (Exception e) {
                    log.warn("删除临时文件失败：{} - {}", fileId, e.getMessage());
                }
            }
        }
    }
    
    // ==================== 辅助工具 ====================
    
    private static boolean isAllEmpty(Object[] row) {
        for (Object cell : row) {
            if (cell != null) {
                String str = cell.toString().trim();
                if (!str.isEmpty() && !MISSING_VALUE_MARKER.equals(str)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private static boolean isMissingValue(Object val) {
        if (val == null) return true;
        String str = val.toString().trim();
        return str.isEmpty() || MISSING_VALUE_MARKER.equals(str);
    }
    
    private static boolean rowsEqual(Object[] row1, Object[] row2) {
        if (row1 == row2) return true;
        if (row1 == null || row2 == null) return false;
        if (row1.length != row2.length) return false;
        
        for (int i = 0; i < row1.length; i++) {
            Object v1 = row1[i];
            Object v2 = row2[i];
            
            if (v1 == v2) continue;
            if (v1 == null || v2 == null) return false;
            
            // 尝试字符串比较，忽略类型差异（例如 "123" 和 123）
            if (!v1.toString().equals(v2.toString())) {
                return false;
            }
        }
        return true;
    }
    
    // ==================== 内部数据类 ====================
    /**
     * 文本段落类
     */
    @lombok.Data
    static class TextChunk {
        private int chunkId;
        private String content;
        private int startPos;
        private int endPos;
        
        public int getChunkId() { return chunkId; }
        public void setChunkId(int chunkId) { this.chunkId = chunkId; }
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
    static class ChunkResult {
        private int chunkId;
        private int rowCount;
        private String primaryKeyColumn;
        private Map<String, Object> cellData;
        
        public int getChunkId() { return chunkId; }
        public void setChunkId(int chunkId) { this.chunkId = chunkId; }
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
        public String getPrimaryKeyColumn() { return primaryKeyColumn; }
        public void setPrimaryKeyColumn(String primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }
        public Map<String, Object> getCellData() { return cellData; }
        public void setCellData(Map<String, Object> cellData) { this.cellData = cellData; }
    }
}
