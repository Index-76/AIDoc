package com.project.aidoc.common.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工作表提取工具类
 * 用于从Excel文件中提取各个工作表
 */
public class ExcelSheetExtractorUtil {
    
    /**
     * 提取Excel中的所有工作表，返回每个工作表的名称和表头（第一行）
     * @param excelContent Excel文件字节内容
     * @return 工作表信息列表，每个元素包含 sheetName 和 headers (List<String>)
     * @throws Exception 解析失败时抛出
     */
    public static List<Map<String, Object>> extractSheets(byte[] excelContent) throws Exception {
        List<Map<String, Object>> sheets = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelContent))) {
            int sheetNum = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetNum; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                
                // 获取第一行作为表头
                Row headerRow = sheet.getRow(0);
                List<String> headers = new ArrayList<>();
                if (headerRow != null) {
                    for (Cell cell : headerRow) {
                        headers.add(cell.getStringCellValue());
                    }
                }
                
                Map<String, Object> sheetInfo = new HashMap<>();
                sheetInfo.put("sheetName", sheetName);
                sheetInfo.put("headers", headers);
                sheets.add(sheetInfo);
            }
        }
        return sheets;
    }
}