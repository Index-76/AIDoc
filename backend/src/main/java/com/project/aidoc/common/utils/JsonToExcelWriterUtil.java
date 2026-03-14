package com.project.aidoc.common.utils;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JSON到Excel写入工具类
 * 用于将JSON数据写入Excel文件
 */
public class JsonToExcelWriterUtil {
    
    private static final Logger log = LoggerFactory.getLogger(JsonToExcelWriterUtil.class);
    
    /**
     * 填充 Excel 模板
     * @param filledFileId 模板副本文件ID
     * @param mergedData 合并后的数据，key为sheet名称，value为行数据列表
     * @param headerInfo 表头信息，包含sheets列表
     * @param userId 用户ID
     * @param fileService 文件服务
     * @return 更新后的文件ID
     * @throws Exception 处理失败时抛出
     */
    public static String fillExcelTemplate(String filledFileId, Map<String, List<Object[]>> mergedData,
                                          Map<String, Object> headerInfo,
                                          String userId, FileService fileService) throws Exception {
        log.info("步骤 6: 填充 Excel 模板");
        
        byte[] templateContent = fileService.getFileContent(filledFileId, userId);
        if (templateContent == null) {
            throw new Exception("获取模板副本内容失败");
        }
        
        byte[] outputContent;
        
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templateContent))) {
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");
            
            // 按工作表顺序写入
            for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
                Map<String, Object> sheetInfo = sheets.get(sheetIndex);
                String sheetName = (String) sheetInfo.get("sheetName");
                
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    sheet = workbook.createSheet(sheetName);
                }
                
                List<Object[]> rows = mergedData.get(sheetName);
                if (rows == null || rows.isEmpty()) {
                    log.warn("工作表 '{}' 没有数据可写入", sheetName);
                    continue;
                }
                
                // 从第 1 行开始写入（第 0 行是表头）
                int rowNum = 1;
                for (Object[] rowData : rows) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null) {
                        row = sheet.createRow(rowNum);
                    }
                    
                    for (int col = 0; col < rowData.length; col++) {
                        Cell cell = row.getCell(col);
                        if (cell == null) {
                            cell = row.createCell(col);
                        }
                        
                        Object value = rowData[col];
                        if (value != null) {
                            setCellValue(cell, value);
                        }
                    }
                    
                    rowNum++;
                }
                
                log.info("工作表 '{}' 写入完成，共 {} 行", sheetName, rows.size());
            }
            
            // 将字节数组输出
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            outputContent = baos.toByteArray();
        }
        
        // 由于无法原地更新，采用删除旧文件 + 保存新文件的方式
        // 保存新文件时保持文件名和区域不变
        File filledFile = fileService.getFileById(filledFileId, userId);
        if (filledFile == null) {
            throw new Exception("找不到模板副本文件：" + filledFileId);
        }
        
        // 获取模板原始名称并构造新文件名
        String templateOriginalName = (String) headerInfo.get("templateOriginalName");
        if (templateOriginalName == null) {
            // 兼容旧数据（若没有该字段，则使用原来的名称）
            templateOriginalName = filledFile.getOriginalName();
        }
        
        // 分离文件名和拓展名，在拓展名前添加"_filled"
        int lastDotIndex = templateOriginalName.lastIndexOf('.');
        String newOriginalName;
        if (lastDotIndex > 0) {
            // 有拓展名：文件名_filled.拓展名
            String fileName = templateOriginalName.substring(0, lastDotIndex);
            String extension = templateOriginalName.substring(lastDotIndex);
            newOriginalName = fileName + "_filled" + extension;
        } else {
            // 无拓展名：直接添加"_filled"
            newOriginalName = templateOriginalName + "_filled";
        }
        
        // 创建新的 MultipartFile
        MultipartFile updatedFile = createMultipartFile(
            filledFile.getFileName(),    // 内部文件名保持不变
            newOriginalName,             // 用户下载时显示的新名称
            filledFile.getContentType(),
            outputContent
        );
        
        // 先保存新文件（生成新 ID）
        File newFile = fileService.saveFile(updatedFile, "temp", userId);
        String newFileId = newFile.getId();
        
        // 删除旧文件
        fileService.deleteFileById(filledFileId, userId);
        
        log.info("Excel 文件已更新，原文件 ID: {}, 新文件 ID: {}", filledFileId, newFileId);
        
        // 返回新的文件 ID
        return newFileId;
    }
    
    /**
     * 设置单元格值
     */
    private static void setCellValue(Cell cell, Object value) {
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    /**
     * 创建 MultipartFile 工具方法
     */
    public static MultipartFile createMultipartFile(String name, String originalFilename, 
                                                      String contentType, byte[] content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return content == null || content.length == 0;
            }

            @Override
            public long getSize() {
                return content != null ? content.length : 0;
            }

            @Override
            public byte[] getBytes() {
                return content;
            }

            @Override
            public ByteArrayInputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(java.io.File dest) {
                // 不需要实现
            }
        };
    }
}