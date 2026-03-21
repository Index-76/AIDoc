package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表头提取工具类（整合 ExcelUtils 功能）
 */
public class HeaderExtractorUtil {

    private static final Logger log = LoggerFactory.getLogger(HeaderExtractorUtil.class);

    /**
     * 提取模板表头信息并保存为 JSON（默认从第0行，1行表头）
     */
    public static Map<String, Object> extractTemplateHeaders(File templateFile, String userId, FileService fileService) throws Exception {
        return extractTemplateHeaders(templateFile, userId, fileService, 0, 1, null);
    }

    /**
     * 提取模板表头信息（完整参数）
     */
    public static Map<String, Object> extractTemplateHeaders(File templateFile, String userId, FileService fileService,
                                                              int headerRowIndex, int headerRowCount, String primaryKeyHeader) throws Exception {
        log.info("步骤 0: 提取模板表头信息，起始行：{}，行数：{}，主键列：{}", headerRowIndex, headerRowCount, primaryKeyHeader);

        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) return null;

        // 调用 ExcelSheetExtractorUtil 提取工作表信息
        List<Map<String, Object>> sheets = ExcelSheetExtractorUtil.extractSheets(templateContent, headerRowIndex, headerRowCount, primaryKeyHeader);
        
        for (Map<String, Object> sheet : sheets) {
            sheet.put("fileName", templateFile.getOriginalName());
        }

        Map<String, Object> headerInfo = new HashMap<>();
        headerInfo.put("fileId", templateFile.getId());
        headerInfo.put("sheetNum", sheets.size());
        headerInfo.put("sheets", sheets);
        headerInfo.put("headerRowIndex", headerRowIndex);
        headerInfo.put("headerRowCount", headerRowCount);
        headerInfo.put("templateOriginalName", templateFile.getOriginalName());

        // 输出日志
        for (int i = 0; i < sheets.size(); i++) {
            Map<String, Object> sheetInfo = sheets.get(i);
            String sheetName = (String) sheetInfo.get("sheetName");
            List<String> headers = (List<String>) sheetInfo.get("headers");
            log.info("工作表 {}: {} - {} 个列头", i, sheetName, headers.size());
        }

        // 保存表头信息到临时区
        String savedFileId = saveHeaderInfo(headerInfo, templateFile.getId(), userId, fileService);
        headerInfo.put("fileId", savedFileId);
        log.info("表头信息已保存到临时区，文件 ID: {}", savedFileId);
        return headerInfo;
    }

    /**
     * 保存表头信息为 JSON 文件
     */
    private static String saveHeaderInfo(Map<String, Object> headerInfo, String templateFileId, String userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(headerInfo);
        MultipartFile headerFile = JsonToExcelWriterUtil.createMultipartFile(
                templateFileId + "_headers.json",
                templateFileId + "_headers.json",
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );
        File savedFile = fileService.saveFile(headerFile, "temp", userId);
        return savedFile.getId();
    }
}