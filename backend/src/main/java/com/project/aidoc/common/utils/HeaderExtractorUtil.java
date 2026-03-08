package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表头提取工具类
 * 用于从Excel表格中提取表头参数
 */
public class HeaderExtractorUtil {
    
    private static final Logger log = LoggerFactory.getLogger(HeaderExtractorUtil.class);
    
    /**
     * 提取模板的表头信息并保存为 JSON
     * @param templateFile 模板文件
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 包含表头信息的 Map，其中 fileId 字段已更新为保存后的临时文件 ID
     * @throws Exception 当读取或保存失败时抛出异常
     */
    public static Map<String, Object> extractTemplateHeaders(File templateFile, Long userId, 
                                                              FileService fileService) throws Exception {
        log.info("步骤 0: 提取模板表头信息");
        
        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) {
            return null;
        }
        
        // 调用 ExcelSheetExtractorUtil 提取所有 sheet 的表头
        List<Map<String, Object>> sheets = ExcelSheetExtractorUtil.extractSheets(templateContent);
        
        Map<String, Object> headerInfo = new HashMap<>();
        headerInfo.put("fileId", templateFile.getId());
        headerInfo.put("sheetNum", sheets.size());
        headerInfo.put("sheets", sheets);
        // 保存模板文件的原始名称
        headerInfo.put("templateOriginalName", templateFile.getOriginalName());
        
        // 输出日志
        for (int i = 0; i < sheets.size(); i++) {
            Map<String, Object> sheetInfo = sheets.get(i);
            String sheetName = (String) sheetInfo.get("sheetName");
            List<String> headers = (List<String>) sheetInfo.get("headers");
            log.info("工作表 {}: {} - {} 个列头", i, sheetName, headers.size());
        }
        
        // 保存表头信息到临时区，获取保存后的文件 ID
        String savedFileId = saveHeaderInfo(headerInfo, templateFile.getId(), userId, fileService);
        
        // 更新 headerInfo 中的 fileId 为保存后的临时文件 ID
        headerInfo.put("fileId", savedFileId);
        
        log.info("表头信息已保存到临时区，文件 ID: {}", savedFileId);
        
        return headerInfo;
    }
    
    /**
     * 保存表头信息到临时区
     * @param headerInfo 表头信息
     * @param templateFileId 模板文件 ID
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 保存后的文件 ID
     * @throws Exception 当保存失败时抛出异常
     */
    private static String saveHeaderInfo(Map<String, Object> headerInfo, String templateFileId,
                                       Long userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(headerInfo);
        
        MultipartFile headerFile = new MultipartFile() {
            @Override
            public String getName() {
                return templateFileId + "_headers.json";
            }

            @Override
            public String getOriginalFilename() {
                return templateFileId + "_headers.json";
            }

            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return jsonContent.getBytes(StandardCharsets.UTF_8).length;
            }

            @Override
            public byte[] getBytes() {
                return jsonContent.getBytes(StandardCharsets.UTF_8);
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
        
        // 保存文件并获取返回的 File 对象
        File savedFile = fileService.saveFile(headerFile, "temp", userId);
        // 从 File 对象中获取真实的文件 ID
        String savedFileId = savedFile.getId();
        return savedFileId;
    }
}