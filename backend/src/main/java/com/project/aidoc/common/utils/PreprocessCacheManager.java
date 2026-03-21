package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文件预处理缓存管理器
 * 
 * 功能：
 * 1. 检查临时区是否已存在预处理文件（文本文件或 Excel 摘要文件）
 * 2. 如果存在则直接复用，避免重复的 I/O 和计算开销
 * 3. 如果不存在则生成并保存预处理文件
 * 4. 支持配置项控制是否保留预处理文件
 * 
 * @author AI Assistant
 * @date 2026-03-21
 */
public class PreprocessCacheManager {
    private static final Logger log = LoggerFactory.getLogger(PreprocessCacheManager.class);

    // 配置参数（可通过系统属性覆盖）
    private static boolean KEEP_TEXT_FILES = Boolean.parseBoolean(
            System.getProperty("template.keep.text.files", "true"));
    private static boolean KEEP_EXCEL_SUMMARY = Boolean.parseBoolean(
            System.getProperty("template.keep.excel.summary", "true"));

    /**
     * 确保文件预处理完成（生成文本/摘要文件并缓存于 temp 区）
     * 如果缓存中已存在预处理文件，则直接复用；否则生成并保存
     * 
     * @param readFiles 原始文件列表
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 预处理后的文件摘要列表
     */
    public static List<AISwitchFilesForFormFiller.FileSummary> ensurePreprocessedFiles(
            List<File> readFiles, String userId, FileService fileService) throws Exception {
        
        log.info("========== 开始确保文件预处理完成（带缓存检查） ==========");
        List<AISwitchFilesForFormFiller.FileSummary> summaries = new ArrayList<>();

        for (File file : readFiles) {
            try {
                String fileId = file.getId();
                String originalName = file.getOriginalName();
                String fileExt = getFileExtension(originalName).toLowerCase();

                // 1. 检查是否已有预处理结果
                String expectedTextFileName = fileId + "_text.txt";
                String expectedSummaryFileName = fileId + "_summary.json";
                
                AISwitchFilesForFormFiller.FileSummary summary;

                if (isTextFile(fileExt)) {
                    // 文本文件：检查是否有现成的文本文件
                    File existingTextFile = findExistingFile(expectedTextFileName, userId, fileService);
                    
                    if (existingTextFile != null && KEEP_TEXT_FILES) {
                        // 缓存命中：直接使用现有文本文件作为预处理结果
                        log.info("缓存命中：文本文件 {} 已存在 (ID: {})", expectedTextFileName, existingTextFile.getId());
                        summary = buildFileSummaryFromTextFile(file, existingTextFile, userId, fileService);
                    } else {
                        // 未命中或禁用缓存：重新生成
                        log.info("未找到文本文件或禁用缓存，开始转换：{}", originalName);
                        summary = generateTextFilePreprocessing(file, userId, fileService, expectedTextFileName);
                    }
                    
                } else if (isExcelFile(fileExt)) {
                    // Excel 文件：检查是否有摘要文件
                    File existingSummaryFile = findExistingFile(expectedSummaryFileName, userId, fileService);
                    
                    if (existingSummaryFile != null && KEEP_EXCEL_SUMMARY) {
                        // 缓存命中：直接使用现有摘要文件
                        log.info("缓存命中：Excel 摘要文件 {} 已存在 (ID: {})", expectedSummaryFileName, existingSummaryFile.getId());
                        summary = buildFileSummaryFromSummaryFile(file, existingSummaryFile, userId, fileService);
                    } else {
                        // 未命中或禁用缓存：重新生成
                        log.info("未找到 Excel 摘要文件或禁用缓存，开始生成：{}", originalName);
                        summary = generateExcelFilePreprocessing(file, userId, fileService, expectedSummaryFileName);
                    }
                    
                } else {
                    // 其他类型（Word, PDF 等）转换为纯文本
                    File existingTextFile = findExistingFile(expectedTextFileName, userId, fileService);
                    
                    if (existingTextFile != null && KEEP_TEXT_FILES) {
                        // 缓存命中
                        log.info("缓存命中：转换后的文本文件 {} 已存在 (ID: {})", expectedTextFileName, existingTextFile.getId());
                        summary = buildFileSummaryFromTextFile(file, existingTextFile, userId, fileService);
                    } else {
                        // 未命中或禁用缓存：重新转换
                        log.info("未找到转换后的文本文件或禁用缓存，开始转换：{}", originalName);
                        summary = generateOtherFilePreprocessing(file, userId, fileService, expectedTextFileName);
                    }
                }

                if (summary != null) {
                    summaries.add(summary);
                }
                
            } catch (Exception e) {
                log.error("预处理文件 {} 失败：{}", file.getId(), e.getMessage(), e);
            }
        }
        
        log.info("========== 文件预处理完成，共 {} 个文件 ==========", summaries.size());
        return summaries;
    }

    /**
     * 在 temp 和 result 区查找指定的文件
     */
    private static File findExistingFile(String targetFileName, String userId, FileService fileService) {
        try {
            // 先在 temp 区查找
            List<File> tempFiles = fileService.getFilesByUserIdAndSection(userId, "temp");
            for (File f : tempFiles) {
                if (targetFileName.equals(f.getFileName()) || targetFileName.equals(f.getOriginalName())) {
                    return f;
                }
            }
            
            // 再在 result 区查找
            List<File> resultFiles = fileService.getFilesByUserIdAndSection(userId, "result");
            for (File f : resultFiles) {
                if (targetFileName.equals(f.getFileName()) || targetFileName.equals(f.getOriginalName())) {
                    return f;
                }
            }
        } catch (Exception e) {
            log.error("查找文件失败：{} - {}", targetFileName, e.getMessage());
        }
        return null;
    }

    /**
     * 从文本文件构建 FileSummary
     */
    private static AISwitchFilesForFormFiller.FileSummary buildFileSummaryFromTextFile(
            File originalFile, File textFile, String userId, FileService fileService) throws Exception {
        
        AISwitchFilesForFormFiller.FileSummary summary = new AISwitchFilesForFormFiller.FileSummary();
        summary.setFileId(originalFile.getId());
        summary.setOriginalFileId(originalFile.getId());
        summary.setFileName(originalFile.getOriginalName());
        summary.setFileType(getFileType(originalFile.getOriginalName()));

        byte[] content = fileService.getFileContent(textFile.getId(), userId);
        if (content != null) {
            String text = new String(content, StandardCharsets.UTF_8);
            summary.setSummary(text.length() > 200 ? text.substring(0, 200) : text);
        }
        
        return summary;
    }

    /**
     * 从 Excel 摘要文件构建 FileSummary
     */
    private static AISwitchFilesForFormFiller.FileSummary buildFileSummaryFromSummaryFile(
            File originalFile, File summaryFile, String userId, FileService fileService) throws Exception {
        
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonBytes = fileService.getFileContent(summaryFile.getId(), userId);
        ExcelDataProcessor.ExcelSummary excelSummary = mapper.readValue(jsonBytes, ExcelDataProcessor.ExcelSummary.class);

        AISwitchFilesForFormFiller.FileSummary summary = new AISwitchFilesForFormFiller.FileSummary();
        summary.setFileId(originalFile.getId());
        summary.setOriginalFileId(originalFile.getId());
        summary.setFileName(originalFile.getOriginalName());
        summary.setFileType("Excel");
        summary.setSummaryFilePath(summaryFile.getId());

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
     * 生成文本文件的预处理结果
     */
    private static AISwitchFilesForFormFiller.FileSummary generateTextFilePreprocessing(
            File file, String userId, FileService fileService, String expectedTextFileName) throws Exception {
        
        byte[] content = fileService.getFileContent(file.getId(), userId);
        if (content == null) {
            throw new Exception("无法获取文件内容：" + file.getId());
        }
        
        String textContent = new String(content, StandardCharsets.UTF_8);
        saveTextFile(textContent, expectedTextFileName, userId, fileService);
        
        return buildFileSummaryForText(file, textContent);
    }

    /**
     * 生成 Excel 文件的预处理结果
     */
    private static AISwitchFilesForFormFiller.FileSummary generateExcelFilePreprocessing(
            File file, String userId, FileService fileService, String expectedSummaryFileName) throws Exception {
        
        byte[] content = fileService.getFileContent(file.getId(), userId);
        if (content == null) {
            throw new Exception("无法获取 Excel 文件内容：" + file.getId());
        }
        
        ExcelDataProcessor.ExcelSummary excelSummary = ExcelDataProcessor.generateExcelSummary(content, file.getId());
        saveJsonFile(excelSummary, expectedSummaryFileName, userId, fileService);
        
        return buildFileSummaryForExcel(file, excelSummary);
    }

    /**
     * 生成其他类型文件的预处理结果（转换为文本）
     */
    private static AISwitchFilesForFormFiller.FileSummary generateOtherFilePreprocessing(
            File file, String userId, FileService fileService, String expectedTextFileName) throws Exception {
        
        byte[] content = fileService.getFileContent(file.getId(), userId);
        if (content == null) {
            throw new Exception("无法获取文件内容：" + file.getId());
        }
        
        String textContent = ExtractText.convertToText(content, file.getOriginalName());
        saveTextFile(textContent, expectedTextFileName, userId, fileService);
        
        return buildFileSummaryForText(file, textContent);
    }

    /**
     * 辅助方法：保存文本文件到 temp 区
     */
    private static void saveTextFile(String textContent, String fileName, String userId, FileService fileService) {
        try {
            MultipartFile multipartFile = JsonToExcelWriterUtil.createMultipartFile(
                    fileName, fileName, "text/plain", textContent.getBytes(StandardCharsets.UTF_8));
            fileService.saveFile(multipartFile, "temp", userId);
            log.debug("文本文件已保存：{}", fileName);
        } catch (Exception e) {
            log.error("保存文本文件失败：{} - {}", fileName, e.getMessage());
        }
    }

    /**
     * 辅助方法：保存 JSON 摘要文件
     */
    private static void saveJsonFile(Object obj, String fileName, String userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonBytes = mapper.writeValueAsBytes(obj);
        MultipartFile multipartFile = JsonToExcelWriterUtil.createMultipartFile(
                fileName, fileName, "application/json", jsonBytes);
        fileService.saveFile(multipartFile, "temp", userId);
        log.debug("JSON 文件已保存：{}", fileName);
    }

    /**
     * 辅助方法：为文本文件构建 FileSummary
     */
    private static AISwitchFilesForFormFiller.FileSummary buildFileSummaryForText(File file, String textContent) {
        AISwitchFilesForFormFiller.FileSummary summary = new AISwitchFilesForFormFiller.FileSummary();
        summary.setFileId(file.getId());
        summary.setOriginalFileId(file.getId());
        summary.setFileName(file.getOriginalName());
        summary.setFileType(getFileType(file.getOriginalName()));
        summary.setSummary(textContent.length() > 200 ? textContent.substring(0, 200) : textContent);
        return summary;
    }

    /**
     * 辅助方法：为 Excel 文件构建 FileSummary
     */
    private static AISwitchFilesForFormFiller.FileSummary buildFileSummaryForExcel(
            File file, ExcelDataProcessor.ExcelSummary excelSummary) throws Exception {
        
        AISwitchFilesForFormFiller.FileSummary summary = new AISwitchFilesForFormFiller.FileSummary();
        summary.setFileId(file.getId());
        summary.setOriginalFileId(file.getId());
        summary.setFileName(file.getOriginalName());
        summary.setFileType("Excel");
        
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
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 判断是否为文本文件
     */
    private static boolean isTextFile(String ext) {
        return "txt".equals(ext) || "md".equals(ext) || "markdown".equals(ext);
    }

    /**
     * 判断是否为 Excel 文件
     */
    private static boolean isExcelFile(String ext) {
        return "xlsx".equals(ext) || "xls".equals(ext);
    }

    /**
     * 获取文件类型
     */
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
}
