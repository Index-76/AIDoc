package com.project.aidoc.service;

import com.project.aidoc.common.enums.ToolType;
import com.project.aidoc.common.tools.*;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.FileRepository;
import com.project.aidoc.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 工具执行服务
 */
@Slf4j
@Service
public class ToolExecutionService {

    @Autowired
    private DirectoryViewerTool directoryViewerTool;

    @Autowired
    private ContentSummarizerTool contentSummarizerTool;

    @Autowired
    private FormatConverterTool formatConverterTool;

    @Autowired
    private SmartFormFillerTool smartFormFillerTool;

    @Autowired
    private SmartEditorTool smartEditorTool;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserConfigService userConfigService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    // 用于并行处理文件总结的线程池（最大 3 个并发）
    private final ExecutorService summaryExecutor = Executors.newFixedThreadPool(3);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 执行指定工具
     * 
     * @param userId      用户ID
     * @param toolCode    工具代码
     * @param userMessage 用户消息
     * @param sessionId   会话ID
     * @return 工具执行结果
     */
    public String executeTool(Long userId, int toolCode, String userMessage, String sessionId) {
        ToolType toolType = ToolType.fromCode(toolCode);

        log.info("开始执行工具: {} ({})", toolType.getDescription(), toolCode);

        try {
            String result;

            switch (toolType) {
                case DIRECTORY_VIEW:
                    // 获取用户文件目录
                    List<File> userFiles = fileService.getFilesByUserId(userId);
                    result = directoryViewerTool.generateFixedFormatDirectoryOutput(userFiles);
                    break;
                case CONTENT_SUMMARY:
                    // 智能处理内容总结请求
                    result = handleContentSummary(userId, userMessage);
                    break;
                case FORMAT_CONVERSION:
                    // 智能处理格式转换请求
                    result = handleFormatConversion(userId, userMessage);
                    break;
                case SMART_FILL:
                    // 执行智能填表功能
                    result = smartFormFillerTool.executeFillForm(userId, userMessage);
                    break;
                case SMART_MODIFY:
                    String editPath = extractFilePathFromMessage(userMessage);
                    String editReq = extractEditRequirements(userMessage);
                    Map<String, Object> editInfo = smartEditorTool.editDocument(editPath, editReq);
                    result = formatEditResult(editInfo);
                    break;
                default:
                    result = "未识别的工具类型: " + toolCode;
                    break;
            }

            log.info("工具执行完成: {} ({})", toolType.getDescription(), toolCode);
            return result;

        } catch (Exception e) {
            log.error("工具执行失败: {} ({})", toolType.getDescription(), toolCode, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 从用户消息中提取文件路径或文件名
     * 
     * 支持多种格式：
     * 1. 完整路径：/path/to/file.txt
     * 2. 带扩展名的文件名：report.pdf, 工作总结.docx
     * 3. 自然语言描述："将 xxx.pdf 转换为 word"中的"xxx.pdf"
     */
    private String extractFilePathFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        log.debug("尝试从消息中提取文件路径或文件名：{}", message);

        // === 策略 1：尝试提取完整的文件路径（包含 / 或 \）===
        String[] parts = message.split("[\\s:：,，]+");
        for (String part : parts) {
            if (part.contains(".") && (part.contains("/") || part.contains("\\"))) {
                log.info("提取到完整文件路径：{}", part);
                return part;
            }
        }

        // === 策略 2：使用正则表达式匹配带扩展名的文件名 ===
        // 常见文档扩展名
        String filePattern = "([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]+\\.(pdf|docx|doc|xlsx|xls|txt|md|markdown|csv|pptx|ppt))";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filePattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            String fileName = matcher.group(1).trim();
            // 清理首尾的标点符号
            fileName = fileName.replaceAll("^[,，.。:\\s]+", "")
                              .replaceAll("[,，.。:\\s]+$", "");
            log.info("通过正则匹配到文件名：{}", fileName);
            return fileName;
        }

        // === 策略 3：尝试从特定句式中提取文件名 ===
        // 例如："将 xxx.pdf 转换为 word"、"转换 xxx.docx 为 pdf"
        String[] extractPatterns = {
            "将\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]+\\.[a-zA-Z0-9]+)\\s*(?:转换|转|变为)",
            "转换\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]+\\.[a-zA-Z0-9]+)\\s*(?:为|成|到)",
            "把\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]+\\.[a-zA-Z0-9]+)\\s*(?:转换|转|变为)"
        };
        
        for (String extractPattern : extractPatterns) {
            java.util.regex.Pattern extractPat = java.util.regex.Pattern.compile(extractPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher extractMat = extractPat.matcher(message);
            
            if (extractMat.find()) {
                String fileName = extractMat.group(1).trim();
                // 清理首尾的标点符号和空格
                fileName = fileName.replaceAll("^[,，.。:\\s]+", "")
                                  .replaceAll("[,，.。:\\s]+$", "");
                log.info("通过句式模式匹配到文件名：{}", fileName);
                return fileName;
            }
        }

        log.debug("未从消息中提取到有效的文件路径或文件名");
        return null; // 未找到文件
    }

    /**
     * 从用户消息中提取总结要求
     */
    private String extractSummaryRequirements(String message) {
        return "请总结文件的主要内容";
    }

    /**
     * 从用户消息中提取转换要求
     */
    private String extractConversionRequirements(String message) {
        return "请将文件转换为目标格式";
    }

    /**
     * 从用户消息中提取模板路径
     */
    private String extractTemplatePathFromMessage(String message) {
        return "template.xlsx";
    }

    /**
     * 从用户消息中提取填表要求
     */
    private String extractFillRequirements(String message) {
        return "请根据读取内容填写模板";
    }

    /**
     * 从用户消息中提取编辑要求
     */
    private String extractEditRequirements(String message) {
        return "请修改文档内容";
    }

    /**
     * 从用户消息中提取文件类型
     */
    private String extractFileTypeFromMessage(String message) {
        if (message.contains("Excel") || message.contains("xlsx")) {
            return "Excel";
        } else if (message.contains("Word") || message.contains("docx")) {
            return "Word";
        }
        return "Unknown";
    }

    /**
     * 格式化目录查看结果
     */
    private String formatDirectoryResult(Map<String, Object> dirInfo) {
        return "目录查看结果: " + dirInfo.toString();
    }

    /**
     * 格式化内容总结结果
     */
    private String formatSummaryResult(Map<String, Object> summaryInfo) {
        return "内容总结结果: " + summaryInfo.toString();
    }

    /**
     * 格式化格式转换结果
     */
    private String formatConversionResult(Map<String, Object> convInfo) {
        return "格式转换结果: " + convInfo.toString();
    }

    /**
     * 格式化填表结果
     */
    private String formatFillResult(Map<String, Object> fillInfo) {
        return "填表结果: " + fillInfo.toString();
    }

    /**
     * 格式化编辑结果
     */
    private String formatEditResult(Map<String, Object> editInfo) {
        return "编辑结果: " + editInfo.toString();
    }

    /**
     * 检查工具是否可用
     */
    public boolean isToolAvailable(int toolCode) {
        try {
            ToolType toolType = ToolType.fromCode(toolCode);
            return toolType != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 智能处理内容总结请求
     */
    private String handleContentSummary(Long userId, String userMessage) {
        try {
            // 1. 尝试从消息中提取目标区域
            String targetSection = extractSectionFromMessage(userMessage);

            // 2. 如果用户没有指定区域，默认使用读取区（read）
            if (targetSection == null || targetSection.isEmpty()) {
                targetSection = "read";
            }

            // 3. 获取用户的完整目录信息（用于 AI 理解文件分布）
            List<File> allUserFiles = fileService.getFilesByUserId(userId);
            String directoryContext = generateDirectoryContext(allUserFiles, targetSection);

            // 4. 尝试从消息中提取文件路径或文件名
            String filePath = extractFilePathFromMessage(userMessage);

            // 5. 如果指定了具体文件，总结单个文件
            if (filePath != null && !filePath.isEmpty() && !"sample.txt".equals(filePath)) {
                // 获取用户指定区域的所有文件
                List<File> userFiles = fileService.getFilesByUserIdAndSection(userId, targetSection);

                // 过滤掉缓存文件
                List<File> realFiles = filterOutCacheFiles(userFiles);

                // 尝试匹配用户指定的文件
                File matchedFile = findMatchingFile(realFiles, filePath);

                if (matchedFile != null) {
                    // 找到匹配的文件，使用文件的完整路径
                    String summaryReq = extractSummaryRequirements(userMessage);

                    // 构建包含目录上下文的提示词
                    StringBuilder contextPrompt = new StringBuilder();
                    contextPrompt.append("当前用户的文件目录结构如下：\n");
                    contextPrompt.append(directoryContext).append("\n\n");
                    contextPrompt.append("用户要求总结的文件是：").append(matchedFile.getOriginalName()).append("\n");
                    contextPrompt.append("请基于这个文件进行总结，忽略其他文件。\n\n");

                    // 调用总结工具
                    Map<String, Object> summaryInfo = contentSummarizerTool.summarizeContent(
                            matchedFile.getFilePath(),
                            summaryReq,
                            userId);
                    return formatSummaryResultWithContext(summaryInfo, contextPrompt.toString());
                } else {
                    // 没有找到匹配的文件
                    String sectionName = getSectionDisplayName(targetSection);
                    return "在" + sectionName + "未找到名为 \"" + filePath + "\" 的文件。请使用文件全名或先查看目录确认文件名。";
                }
            }

            // 6. 如果没有指定具体文件，批量总结指定区域的所有文件
            return batchSummarizeAllFiles(userId, userMessage);

        } catch (Exception e) {
            log.error("处理内容总结请求失败", e);
            return "处理总结请求时发生错误：" + e.getMessage();
        }
    }

    /**
     * 生成目录上下文信息（用于帮助 AI 理解文件分布）
     */
    private String generateDirectoryContext(List<File> allFiles, String targetSection) {
        StringBuilder context = new StringBuilder();

        // 按区域分组统计（排除缓存文件和文本文件）
        Map<String, List<File>> filesBySection = allFiles.stream()
                .filter(f -> !f.getFileName().endsWith("_summary.json"))
                .filter(f -> !f.getFileName().endsWith("_text.txt"))
                .collect(Collectors.groupingBy(File::getSection));

        // 输出各个区域的文件数量
        String[] sections = { "read", "wait", "template", "result" };
        boolean hasAnyFiles = false;

        for (String section : sections) {
            List<File> sectionFiles = filesBySection.getOrDefault(section, new ArrayList<>());
            String displayName = getSectionDisplayName(section);

            if (!sectionFiles.isEmpty()) {
                hasAnyFiles = true;
                context.append(displayName).append("：").append(sectionFiles.size()).append(" 个文件\n");

                // 如果是目标区域，列出文件名
                if (section.equals(targetSection)) {
                    for (File file : sectionFiles) {
                        context.append("  - ").append(file.getOriginalName()).append("\n");
                    }
                } else {
                    // 非目标区域也显示文件名，帮助用户了解文件分布
                    for (File file : sectionFiles) {
                        context.append("  - ").append(file.getOriginalName()).append("\n");
                    }
                }
            } else {
                // 空区域也要显示
                context.append(displayName).append("：0 个文件\n");
            }
        }

        // 如果所有区域都没有文件，给出友好提示
        if (!hasAnyFiles) {
            context.append("\n提示：您还没有上传任何文件。\n");
            context.append("您可以先上传需要总结的文档，然后我可以帮您总结。\n");
        }

        return context.toString();
    }

    /**
     * 格式化包含目录上下文的总结结果
     */
    private String formatSummaryResultWithContext(Map<String, Object> summaryInfo, String context) {
        if (!"success".equals(summaryInfo.get("status"))) {
            return "总结失败：" + summaryInfo.get("message");
        }

        StringBuilder result = new StringBuilder();
        result.append(context).append("\n");

        // 添加总结内容
        Object contentObj = summaryInfo.get("content");
        if (contentObj != null) {
            result.append("\n总结结果：\n");
            result.append(contentObj.toString());
        }

        return result.toString();
    }

    /**
     * 批量总结所有文件 - 每个文件独立并行处理，最后拼接输出
     */
    private String batchSummarizeAllFiles(Long userId, String userMessage) {
        try {
            // 1. 尝试从消息中提取目标区域（read/wait/template/result）
            String targetSection = extractSectionFromMessage(userMessage);

            // 2. 如果用户没有指定区域，默认只总结读取区（read）的文件
            if (targetSection == null || targetSection.isEmpty()) {
                targetSection = "read";
            }

            // 3. 获取用户的完整目录信息（用于 AI 理解文件分布）
            List<File> allUserFiles = fileService.getFilesByUserId(userId);
            String directoryContext = generateDirectoryContext(allUserFiles, targetSection);

            // 4. 获取指定区域的真实文件（排除 temp 区的缓存文件）
            List<File> sectionFiles = fileService.getFilesByUserIdAndSection(userId, targetSection);

            if (sectionFiles.isEmpty()) {
                String sectionName = getSectionDisplayName(targetSection);
                // 使用目录上下文给出更友好的提示
                StringBuilder tipBuilder = new StringBuilder();
                tipBuilder.append("您好！目前您的").append(sectionName).append("是空的，没有任何文件可以总结。\n\n");
                tipBuilder.append("根据当前文件目录情况：\n");
                tipBuilder.append(directoryContext).append("\n");
                tipBuilder.append("您可以：\n");
                tipBuilder.append("1. **上传文件**：将需要总结的文档上传到").append(sectionName).append("。\n");
                tipBuilder.append("2. **指定其他区域**：如果您想总结其他位置（如读取区）的文档，请告诉我。\n\n");
                tipBuilder.append("我随时可以为您提供文档总结服务。");
                return tipBuilder.toString();
            }

            // 5. 过滤掉 temp 区的缓存文件（文件名包含 _summary.json 的）
            List<File> realFiles = filterOutCacheFiles(sectionFiles);

            if (realFiles.isEmpty()) {
                return "您指定的区域没有找到可总结的文件。";
            }

            // 6. 提取用户的总结要求
            String summaryReq = extractSummaryRequirements(userMessage);

            // 7. 为每个文件创建独立的总结任务（并行执行）
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (File sourceFile : realFiles) {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return summarizeSingleFileWithCache(sourceFile, userId, summaryReq);
                    } catch (Exception e) {
                        log.error("总结文件失败：{}", sourceFile.getOriginalName(), e);
                        return "文档《" + sourceFile.getOriginalName() + "》总结失败：" + e.getMessage();
                    }
                }, summaryExecutor);
                futures.add(future);
            }

            // 8. 等待所有任务完成并收集结果
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("好的，已成功为您总结").append(getSectionDisplayName(targetSection)).append("的文件。概括如下：\n\n");

            for (int i = 0; i < futures.size(); i++) {
                try {
                    String summary = futures.get(i).get(); // 等待当前任务完成
                    resultBuilder.append(summary).append("\n\n");
                } catch (Exception e) {
                    log.error("获取总结结果失败", e);
                    resultBuilder.append("文档").append(i + 1).append("：获取失败\n\n");
                }
            }

            // 9. 添加综合说明
            if (realFiles.size() > 1) {
                resultBuilder.append("📊 综合分析：以上 ").append(realFiles.size()).append(" 个文档已分别完成总结。\n");
            }

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("批量总结失败", e);
            return "批量总结失败：" + e.getMessage();
        }
    }

    /**
     * 总结单个文件（带缓存检查和写入）
     */
    private String summarizeSingleFileWithCache(File sourceFile, Long userId, String summaryReq) {
        try {
            // 1. 检查是否存在总结缓存
            String cacheFileName = sourceFile.getId() + "_summary.json";
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");

            for (File tempFile : tempFiles) {
                if (tempFile.getFileName().equals(cacheFileName)) {
                    // 找到缓存，直接返回缓存内容
                    byte[] cacheContent = fileService.getFileContent(tempFile.getId(), userId);
                    if (cacheContent != null && cacheContent.length > 0) {
                        log.info("使用总结缓存：{}", sourceFile.getOriginalName());
                        String cachedSummary = new String(cacheContent, StandardCharsets.UTF_8);
                        // 解析 JSON 并提取 summary 字段
                        return parseJsonSummary(cachedSummary, sourceFile.getOriginalName());
                    }
                }
            }

            // 2. 没有缓存，获取文档内容并调用 AI 分析
            String documentContent = getDocumentContentForSummary(sourceFile, userId);
            if (documentContent == null || documentContent.isEmpty()) {
                return "文档《" + sourceFile.getOriginalName() + "》无法读取内容";
            }

            // 3. 构建独立的 AI 提示词
            StringBuilder singlePrompt = new StringBuilder();
            singlePrompt.append("请对以下**单个文档**进行分析和总结。\n\n");
            singlePrompt.append("**重要说明**：\n");
            singlePrompt.append("1. 这是**独立的单个文件分析**，请专注于当前文档的内容\n");
            singlePrompt.append("2. **不要参考其他任何文件**，只基于当前文档进行分析\n");
            singlePrompt.append("3. 文件目录信息仅用于标识文件来源，与总结内容无关\n");
            singlePrompt.append("4. 请客观、准确地提取文档的核心信息\n\n");
            singlePrompt.append("总结要求：").append(summaryReq).append("\n\n");
            singlePrompt.append("--- 文档开始 ---\n");
            singlePrompt.append("文件名：").append(sourceFile.getOriginalName()).append("\n");
            singlePrompt.append("文档内容：\n").append(documentContent).append("\n");
            singlePrompt.append("--- 文档结束 ---\n\n");
            singlePrompt.append("请按照以下格式回复（简洁明了）：\n");
            singlePrompt.append("• 性质：说明文档类型和用途\n");
            singlePrompt.append("• 目标：文档的主要目的\n");
            singlePrompt.append("• 核心内容：分点列出关键信息（使用数字序号）\n");

            // 4. 调用 AI 分析
            String aiResponse = callSingleDocumentAnalysis(singlePrompt.toString(), userId);

            if (aiResponse == null || aiResponse.isEmpty()) {
                return "文档《" + sourceFile.getOriginalName() + "》AI 分析失败";
            }

            // 5. 将 AI 总结保存到 temp 区作为缓存
            saveAiSummaryToTemp(sourceFile.getId(), aiResponse, userId);

            // 6. 返回格式化的总结结果
            return formatSingleFileSummary(sourceFile.getOriginalName(), aiResponse);

        } catch (Exception e) {
            log.error("总结单个文件失败：{}", sourceFile.getOriginalName(), e);
            throw new RuntimeException("总结失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从 JSON 缓存中提取并格式化 summary 内容
     */
    private String parseJsonSummary(String jsonContent, String originalName) {
        try {
            // 简单的 JSON 解析，提取 summary 字段
            int summaryStart = jsonContent.indexOf("\"summary\"");
            if (summaryStart == -1) {
                return jsonContent; // 不是 JSON 格式，直接返回
            }

            summaryStart = jsonContent.indexOf(":", summaryStart) + 1;
            while (summaryStart < jsonContent.length() &&
                    (jsonContent.charAt(summaryStart) == ' ' || jsonContent.charAt(summaryStart) == '\n')) {
                summaryStart++;
            }

            if (summaryStart >= jsonContent.length()) {
                return jsonContent;
            }

            // 移除开头的引号
            if (jsonContent.charAt(summaryStart) == '"') {
                summaryStart++;
            }

            int summaryEnd = jsonContent.lastIndexOf("\"");
            if (summaryEnd > summaryStart) {
                String summary = jsonContent.substring(summaryStart, summaryEnd);
                // 转义字符还原
                summary = summary.replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                return formatSingleFileSummary(originalName, summary);
            }

            return jsonContent;
        } catch (Exception e) {
            log.error("解析 JSON 缓存失败", e);
            return jsonContent;
        }
    }

    /**
     * 格式化单个文件的总结结果
     */
    private String formatSingleFileSummary(String fileName, String aiResponse) {
        StringBuilder result = new StringBuilder();
        result.append("文档：《").append(fileName).append("》\n");

        // 检查 AI 回复是否已经包含结构化格式
        if (aiResponse.contains("•") || aiResponse.contains("-") || aiResponse.contains("1.")) {
            // 已经有格式，直接使用
            result.append(aiResponse);
        } else {
            // 没有格式，按段落分割
            String[] paragraphs = aiResponse.split("\n");
            for (String paragraph : paragraphs) {
                if (!paragraph.trim().isEmpty()) {
                    result.append(paragraph.trim()).append("\n");
                }
            }
        }

        return result.toString();
    }

    /**
     * 获取文档内容用于总结
     * 优先级：1. 总结缓存（JSON） > 2. 文本文件（TXT）
     */
    private String getDocumentContentForSummary(File sourceFile, Long userId) {
        try {
            // 1. 首先尝试查找总结缓存
            String cacheFileName = sourceFile.getId() + "_summary.json";
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");

            for (File tempFile : tempFiles) {
                if (tempFile.getFileName().equals(cacheFileName)) {
                    // 找到总结缓存，直接返回缓存的 JSON 内容
                    byte[] cacheContent = fileService.getFileContent(tempFile.getId(), userId);
                    if (cacheContent != null && cacheContent.length > 0) {
                        log.info("使用总结缓存：{}", sourceFile.getOriginalName());
                        return new String(cacheContent, StandardCharsets.UTF_8);
                    }
                }
            }

            // 2. 没有总结缓存，尝试查找文本文件
            // 文本文件名格式：[源文件 id]_text.txt
            String textFileName = sourceFile.getId() + "_text.txt";

            for (File tempFile : tempFiles) {
                if (tempFile.getFileName().equals(textFileName)) {
                    // 找到文本文件，返回文本内容
                    byte[] textContent = fileService.getFileContent(tempFile.getId(), userId);
                    if (textContent != null && textContent.length > 0) {
                        log.info("使用文本文件内容：{}", sourceFile.getOriginalName());
                        return new String(textContent, StandardCharsets.UTF_8);
                    }
                }
            }

            // 3. 也没有文本文件，尝试直接读取源文件（如果是 TXT 格式）
            if (sourceFile.getContentType() != null &&
                    (sourceFile.getContentType().contains("text") ||
                            sourceFile.getContentType().contains("markdown"))) {
                byte[] sourceContent = fileService.getFileContent(sourceFile.getId(), userId);
                if (sourceContent != null && sourceContent.length > 0) {
                    log.info("直接读取源文件：{}", sourceFile.getOriginalName());
                    return new String(sourceContent, StandardCharsets.UTF_8);
                }
            }

            log.warn("无法获取文档内容：{}", sourceFile.getOriginalName());
            return "";

        } catch (Exception e) {
            log.error("获取文档内容失败：{}", sourceFile.getOriginalName(), e);
            return "";
        }
    }

    /**
     * 从用户消息中提取目标区域
     */
    private String extractSectionFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // 支持多种表达方式（不区分大小写）
        String lowerMessage = message.toLowerCase();

        // === 读取区（read）===
        if (lowerMessage.contains("读取区") || lowerMessage.contains("read")) {
            return "read";
        }

        // === 等待区（wait）===
        if (lowerMessage.contains("等待区") || lowerMessage.contains("wait")) {
            return "wait";
        }

        // === 模板区（template）===
        if (lowerMessage.contains("模板区") || lowerMessage.contains("template")) {
            return "template";
        }

        // === 结果区（result）===
        if (lowerMessage.contains("结果区") || lowerMessage.contains("result") ||
                lowerMessage.contains("输出区") || lowerMessage.contains("导出区")) {
            return "result";
        }

        return null; // 没有明确指定区域
    }

    /**
     * 获取区域的显示名称
     */
    private String getSectionDisplayName(String section) {
        switch (section) {
            case "read":
                return "读取区";
            case "wait":
                return "等待区";
            case "template":
                return "模板区";
            case "result":
                return "结果区";
            default:
                return "该区域";
        }
    }

    /**
     * 获取文件类型的描述
     */
    private String getFileTypeDescription(String contentType) {
        if (contentType == null) {
            return "文档";
        }

        if (contentType.contains("word") || contentType.contains("document")) {
            return "Word 文档";
        } else if (contentType.contains("pdf")) {
            return "PDF 文档";
        } else if (contentType.contains("markdown") || contentType.contains("text")) {
            return "文本文件";
        } else if (contentType.contains("excel") || contentType.contains("spreadsheet")) {
            return "Excel 表格";
        } else {
            return "文档";
        }
    }

    /**
     * 调用 AI 分析单个文档（避免多文档混合导致的幻觉）
     */
    private String callSingleDocumentAnalysis(String prompt, Long userId) {
        try {
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
                log.warn("用户 {} 未配置 API Key", userId);
                return "抱歉，AI 服务暂时不可用，请先配置 SiliconFlow API Key";
            }

            log.info("使用 API 配置 - URL: {}, Model: {}", apiUrl, model);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2000); // 单个文件不需要太多 token
            requestBody.put("temperature", 0.5); // 降低随机性，更准确

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    return (String) messageObj.get("content");
                }
            }

            log.warn("AI 调用失败");
            return null;

        } catch (Exception e) {
            log.error("调用 AI 分析单个文档失败", e);
            return null;
        }
    }

    /**
     * 将 AI 的总结结果保存到 temp 区作为缓存
     */
    private void saveAiSummaryToTemp(String sourceFileId, String aiSummary, Long userId) {
        try {
            String cacheFileName = sourceFileId + "_summary.json";

            // 检查是否已存在缓存
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");
            for (File tempFile : tempFiles) {
                if (tempFile.getFileName().equals(cacheFileName)) {
                    log.info("缓存已存在，跳过保存：{}", cacheFileName);
                    return;
                }
            }

            // 将 AI 总结转换为 JSON 格式存储
            // 简单处理：将文本总结包装成 JSON
            String jsonContent = "{\n" +
                    "  \"title\": \"" + sourceFileId + "\",\n" +
                    "  \"summary\": " + escapeJsonString(aiSummary) + ",\n" +
                    "  \"timestamp\": \"" + LocalDateTime.now() + "\"\n" +
                    "}";

            byte[] contentBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            ObjectId summaryObjectId = gridFsTemplate.store(
                    new ByteArrayInputStream(contentBytes),
                    cacheFileName,
                    "application/json");

            // 创建文件实体
            File summaryFile = new File();
            summaryFile.setId(summaryObjectId.toString());
            summaryFile.setFileName(cacheFileName);
            summaryFile.setOriginalName(cacheFileName);
            summaryFile.setContentType("application/json");
            summaryFile.setSize(contentBytes.length);
            summaryFile.setSection("temp");
            summaryFile.setUserId(userId);
            summaryFile.setUploadTime(LocalDateTime.now());
            summaryFile.setFilePath("/api/v1/files/" + summaryObjectId.toString() + "/download");

            fileRepository.save(summaryFile);
            log.info("AI 总结已保存到 temp 区：{}", cacheFileName);

        } catch (Exception e) {
            log.error("保存 AI 总结到 temp 区失败", e);
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 调用 AI 进行多文档综合分析
     */
    private String callAiForMultiDocumentAnalysis(String prompt, Long userId) {
        try {
            // 获取用户的 API Key
            String apiKey = getApiKey(userId);
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("用户 {} 未配置 API Key", userId);
                return generateSimpleMultiDocumentSummary(userId);
            }

            String apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
            String model = "deepseek-ai/DeepSeek-V3.2";

            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.7);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    return (String) messageObj.get("content");
                }
            }

            log.warn("AI 调用失败，使用简单总结");
            return generateSimpleMultiDocumentSummary(userId);

        } catch (Exception e) {
            log.error("调用 AI 进行多文档分析失败", e);
            return generateSimpleMultiDocumentSummary(userId);
        }
    }

    /**
     * 生成简单的多文档总结（当 AI 不可用时）
     */
    private String generateSimpleMultiDocumentSummary(Long userId) {
        List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");
        List<File> textFiles = tempFiles.stream()
                .filter(f -> f.getFileName().endsWith("_text.txt"))
                .collect(Collectors.toList());

        StringBuilder summary = new StringBuilder();
        summary.append("我为您整理了以下 ").append(textFiles.size()).append(" 个文档：\n\n");

        for (int i = 0; i < textFiles.size(); i++) {
            File file = textFiles.get(i);
            summary.append(i + 1).append(". **").append(file.getOriginalName()).append("**\n");
            summary.append("   大小：").append(formatFileSize(file.getSize())).append("\n");
            summary.append("   上传时间：").append(file.getUploadTime()).append("\n\n");
        }

        summary.append("由于 AI 服务暂时不可用，我无法提供更详细的分析。您可以稍后再试，或者告诉我您想了解的具体文档，我可以为您提供更详细的信息。");

        return summary.toString();
    }

    /**
     * 查找匹配的文件（采用三级匹配策略）
     * 
     * 匹配优先级：
     * 1. 精确匹配：完整匹配 originalName 或 fileName（含扩展名）
     * 2. 无扩展名匹配：去除扩展名后进行匹配
     * 3. 宽松模糊匹配：仅当搜索词是文件名的完整子串时才视为匹配
     */
    private File findMatchingFile(List<File> files, String searchName) {
        if (searchName == null || files == null || files.isEmpty()) {
            return null;
        }

        log.debug("开始匹配文件，搜索词：{}, 候选文件数：{}", searchName, files.size());

        // === 第一级：精确匹配文件名（含扩展名）===
        for (File file : files) {
            if (file.getOriginalName().equals(searchName) ||
                    file.getFileName().equals(searchName)) {
                log.info("精确匹配到文件：{} (original: {}, fileName: {})", 
                        searchName, file.getOriginalName(), file.getFileName());
                return file;
            }
        }

        // === 第二级：无扩展名匹配 ===
        String searchNameWithoutExt = removeFileExtension(searchName);
        log.debug("精确匹配失败，尝试无扩展名匹配：{}", searchNameWithoutExt);
        
        for (File file : files) {
            String originalNameWithoutExt = removeFileExtension(file.getOriginalName());
            String fileNameWithoutExt = removeFileExtension(file.getFileName());
            
            if (originalNameWithoutExt.equals(searchNameWithoutExt) ||
                    fileNameWithoutExt.equals(searchNameWithoutExt)) {
                log.info("无扩展名匹配到文件：{} -> {}", searchName, file.getOriginalName());
                return file;
            }
        }

        // === 第三级：宽松模糊匹配（仅当搜索词是完整子串时）===
        log.debug("无扩展名匹配失败，尝试宽松模糊匹配");
        
        for (File file : files) {
            // 忽略大小写进行子串匹配
            if (file.getOriginalName().toLowerCase().contains(searchName.toLowerCase()) ||
                    file.getFileName().toLowerCase().contains(searchName.toLowerCase())) {
                log.warn("宽松模糊匹配到文件：{} -> {} (可能存在误匹配，请用户确认)", 
                        searchName, file.getOriginalName());
                return file;
            }
        }

        log.info("未匹配到文件：{}", searchName);
        return null;
    }

    /**
     * 移除文件扩展名
     */
    private String removeFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            // 确保不是以点开头的隐藏文件
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取 API Key（从用户配置中获取）
     */
    private String getApiKey(Long userId) {
        // 从 MongoDB 中获取用户的配置
        UserConfig config = userConfigService.getUserConfig(userId);
        if (config != null && config.getSiliconFlowApiKey() != null && !config.getSiliconFlowApiKey().isEmpty()) {
            return config.getSiliconFlowApiKey();
        }

        // 如果用户没有配置 API Key，返回 null
        log.warn("用户 {} 未配置 SiliconFlow API Key", userId);
        return null;
    }

    /**
     * 过滤掉缓存文件（_summary.json）
     */
    private List<File> filterOutCacheFiles(List<File> files) {
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (!file.getFileName().endsWith("_summary.json")) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 智能处理格式转换请求（完全参考内容总结的匹配机制）
     */
    private String handleFormatConversion(Long userId, String userMessage) {
        try {
            // 1. 尝试从消息中提取目标区域
            String targetSection = extractSectionFromMessage(userMessage);
            
            // 2. 如果用户没有指定区域，默认使用读取区（read）
            if (targetSection == null || targetSection.isEmpty()) {
                targetSection = "read";
            }
            
            // 3. 从消息中提取目标格式
            String targetFormat = extractTargetFormatFromMessage(userMessage);
            
            // 4. 尝试从消息中提取文件名或路径
            String filePath = extractFilePathFromMessage(userMessage);
            
            log.info("格式转换请求 - 区域：{}, 目标格式：{}, 文件路径：{}", 
                    targetSection, targetFormat, filePath);
            
            // 5. 获取用户指定区域的所有文件
            List<File> sectionFiles = fileService.getFilesByUserIdAndSection(userId, targetSection);
            
            if (sectionFiles.isEmpty()) {
                String sectionName = getSectionDisplayName(targetSection);
                return "您的" + sectionName + "还没有任何文件。请先上传需要转换的文档。";
            }
            
            // 6. 过滤掉缓存文件
            List<File> realFiles = filterOutCacheFiles(sectionFiles);
            
            if (realFiles.isEmpty()) {
                return "您指定的区域没有找到可转换的文件。";
            }
            
            // 7. 如果指定了具体文件，转换单个文件
            if (filePath != null && !filePath.isEmpty()) {
                log.info("用户指定了文件：{}, 在 {} 中查找", filePath, targetSection);
                
                // 尝试匹配用户指定的文件
                File matchedFile = findMatchingFile(realFiles, filePath);
                
                if (matchedFile != null) {
                    log.info("匹配到文件 - ID: {}, OriginalName: {}, FileName: {}", 
                             matchedFile.getId(), matchedFile.getOriginalName(), matchedFile.getFileName());
                    
                    // 确定实际的目标格式
                    String actualTargetFormat = determineTargetFormat(matchedFile, targetFormat);
                    
                    if (actualTargetFormat == null) {
                        return "暂不支持将 " + getFileExtension(matchedFile.getOriginalName()) + 
                               " 格式转换为 " + targetFormat + " 格式。";
                    }
                    
                    // 执行单个文件转换
                    return convertSingleFile(userId, matchedFile, actualTargetFormat);
                } else {
                    // 未找到匹配的文件
                    String sectionName = getSectionDisplayName(targetSection);
                    return "在" + sectionName + "未找到名为 \"" + filePath + "\" 的文件。请使用文件的完整名称，或先查看目录确认文件名。";
                }
            }
            
            // 8. 未指定文件时，批量转换指定区域的所有文件
            log.info("未指定文件，批量转换 {} 的所有文件 ({})", targetSection, realFiles.size());
            return batchConvertFiles(userId, realFiles, targetSection, targetFormat);
            
        } catch (Exception e) {
            log.error("处理格式转换请求失败", e);
            return "处理格式转换请求时发生错误：" + e.getMessage();
        }
    }

    /**
     * 从消息中提取目标格式
     */
    private String extractTargetFormatFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // 检查各种格式关键词
        if (lowerMessage.contains("pdf")) {
            return "pdf";
        } else if (lowerMessage.contains("word") || lowerMessage.contains("docx") || lowerMessage.contains("doc")) {
            return "docx";
        } else if (lowerMessage.contains("excel") || lowerMessage.contains("xlsx") || lowerMessage.contains("xls")) {
            return "xlsx";
        } else if (lowerMessage.contains("txt") || lowerMessage.contains("text") || lowerMessage.contains("文本")) {
            return "txt";
        } else if (lowerMessage.contains("md") || lowerMessage.contains("markdown")) {
            return "md";
        }
        
        return null; // 未明确指定目标格式
    }

    /**
     * 根据源文件确定实际的目标格式
     */
    private String determineTargetFormat(File sourceFile, String requestedFormat) {
        String sourceExt = getFileExtension(sourceFile.getOriginalName());
        
        // 如果用户指定了目标格式，按用户的来
        if (requestedFormat != null && !requestedFormat.isEmpty()) {
            return requestedFormat;
        }
        
        // 用户未指定时，根据源文件类型自动选择
        if ("pdf".equals(sourceExt)) {
            // PDF 文件默认转为 Word
            return "docx";
        } else if ("docx".equals(sourceExt) || "doc".equals(sourceExt) || 
                   "xlsx".equals(sourceExt) || "xls".equals(sourceExt) ||
                   "md".equals(sourceExt) || "markdown".equals(sourceExt) ||
                   "txt".equals(sourceExt)) {
            // 其他文件默认转为 PDF
            return "pdf";
        }
        
        // 不支持的格式
        return null;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 转换单个文件
     */
    private String convertSingleFile(Long userId, File sourceFile, String targetFormat) {
        try {
            log.info("开始转换单个文件：{} -> {}", sourceFile.getOriginalName(), targetFormat);
            
            // 构建转换要求
            String convReq = "转换为" + targetFormat;
            
            // 调用格式转换工具
            Map<String, Object> convInfo = formatConverterTool.convertFormat(convReq, sourceFile.getId());
            
            // 检查结果
            if ("success".equals(convInfo.get("status"))) {
                StringBuilder result = new StringBuilder();
                result.append("✅ 格式转换已完成！\n\n");
                result.append("📄 源文件：").append(sourceFile.getOriginalName()).append("\n");
                result.append("📑 目标格式：").append(targetFormat.toUpperCase()).append("\n");
                result.append("💾 保存位置：结果区（result）\n\n");
                result.append("转换后的文件已保存到结果区，您可以随时查看或下载。");
                return result.toString();
            } else {
                String errorMsg = convInfo.get("message") != null ? 
                                  convInfo.get("message").toString() : "转换失败";
                return "❌ 格式转换失败：" + errorMsg;
            }
            
        } catch (Exception e) {
            log.error("转换文件失败：{}", sourceFile.getOriginalName(), e);
            return "❌ 格式转换失败：" + e.getMessage();
        }
    }

    /**
     * 批量转换文件
     */
    private String batchConvertFiles(Long userId, List<File> files, String targetSection, String targetFormat) {
        try {
            StringBuilder resultBuilder = new StringBuilder();
            int successCount = 0;
            int failedCount = 0;
            
            for (File sourceFile : files) {
                try {
                    // 确定每个文件的实际目标格式
                    String actualTargetFormat = determineTargetFormat(sourceFile, targetFormat);
                    
                    if (actualTargetFormat == null) {
                        // 不支持的转换
                        failedCount++;
                        resultBuilder.append("❌ **").append(sourceFile.getOriginalName())
                                    .append("**：暂不支持该格式转换\n");
                        continue;
                    }
                    
                    // 执行转换
                    String convReq = "转换为" + actualTargetFormat;
                    Map<String, Object> convInfo = formatConverterTool.convertFormat(convReq, sourceFile.getId());
                    
                    if ("success".equals(convInfo.get("status"))) {
                        successCount++;
                        resultBuilder.append("✅ **").append(sourceFile.getOriginalName())
                                    .append("** → ").append(actualTargetFormat.toUpperCase()).append("\n");
                    } else {
                        failedCount++;
                        String errorMsg = convInfo.get("message") != null ? 
                                         convInfo.get("message").toString() : "转换失败";
                        resultBuilder.append("❌ **").append(sourceFile.getOriginalName())
                                    .append("**：").append(errorMsg).append("\n");
                    }
                    
                } catch (Exception e) {
                    log.error("转换文件失败：{}", sourceFile.getOriginalName(), e);
                    failedCount++;
                    resultBuilder.append("❌ **").append(sourceFile.getOriginalName())
                                .append("**：转换失败 - ").append(e.getMessage()).append("\n");
                }
            }
            
            // 构建最终结果
            StringBuilder finalResult = new StringBuilder();
            String sectionName = getSectionDisplayName(targetSection);
            
            if (files.size() == 1) {
                // 单个文件转换
                if (successCount > 0) {
                    File convertedFile = files.get(0);
                    String actualTargetFormat = determineTargetFormat(convertedFile, targetFormat);
                    finalResult.append("✅ 格式转换已完成！\n\n");
                    finalResult.append("📄 源文件：").append(convertedFile.getOriginalName()).append("\n");
                    finalResult.append("📑 目标格式：").append(actualTargetFormat).append("\n");
                    finalResult.append("💾 保存位置：结果区（result）\n\n");
                    finalResult.append("转换后的文件已保存到结果区，您可以随时查看或下载。");
                } else {
                    finalResult.append("❌ 格式转换失败\n\n");
                    finalResult.append(resultBuilder.toString());
                }
            } else {
                // 批量文件转换
                finalResult.append("📊 批量格式转换完成！\n\n");
                finalResult.append("✅ 成功：").append(successCount).append(" 个文件\n");
                if (failedCount > 0) {
                    finalResult.append("❌ 失败：").append(failedCount).append(" 个文件\n");
                }
                finalResult.append("\n转换详情：\n").append(resultBuilder.toString());
                
                if (successCount > 0) {
                    finalResult.append("\n所有转换成功的文件已保存到结果区，您可以随时查看或下载。");
                }
            }
            
            return finalResult.toString();
            
        } catch (Exception e) {
            log.error("批量转换文件失败", e);
            return "批量转换失败：" + e.getMessage();
        }
    }
}
