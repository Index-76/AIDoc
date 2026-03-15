package com.project.aidoc.common.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Excel工作表提取工具类
 * 用于从Excel文件中提取各个工作表信息（表头等）
 */
public class ExcelSheetExtractorUtil {

    /**
     * 提取所有工作表信息（默认从第0行开始，1行表头）
     */
    public static List<Map<String, Object>> extractSheets(byte[] excelContent) throws Exception {
        return extractSheets(excelContent, 0, 1, null);
    }

    /**
     * 提取所有工作表信息（指定表头起始行）
     */
    public static List<Map<String, Object>> extractSheets(byte[] excelContent, int headerRowIndex) throws Exception {
        return extractSheets(excelContent, headerRowIndex, 1, null);
    }

    /**
     * 提取所有工作表信息（指定表头起始行和行数）
     */
    public static List<Map<String, Object>> extractSheets(byte[] excelContent, int headerRowIndex, int headerRowCount) throws Exception {
        return extractSheets(excelContent, headerRowIndex, headerRowCount, null);
    }

    /**
     * 提取所有工作表信息（完整参数）
     * @param excelContent Excel字节内容
     * @param headerRowIndex 表头起始行索引（0-based）
     * @param headerRowCount 表头行数（支持多行合并）
     * @param primaryKeyHeader 可选的主键列名（用于后续处理）
     * @return 工作表信息列表，每个元素包含 sheetName, headers, headerRowIndex, headerRowCount, primaryKeyHeader
     */
    public static List<Map<String, Object>> extractSheets(byte[] excelContent, int headerRowIndex, int headerRowCount, String primaryKeyHeader) throws Exception {
        List<Map<String, Object>> sheets = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelContent))) {
            int sheetNum = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetNum; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                List<String> headers = extractMergedHeaders(sheet, headerRowIndex, headerRowCount);
                Map<String, Object> sheetInfo = new HashMap<>();
                sheetInfo.put("sheetName", sheetName);
                sheetInfo.put("headers", headers);
                sheetInfo.put("headerRowIndex", headerRowIndex);
                sheetInfo.put("headerRowCount", headerRowCount);
                if (primaryKeyHeader != null && !primaryKeyHeader.isEmpty()) {
                    sheetInfo.put("primaryKeyHeader", primaryKeyHeader);
                }
                sheets.add(sheetInfo);
            }
        }
        return sheets;
    }

    /**
     * 合并多行表头（支持跨行合并）
     */
    private static List<String> extractMergedHeaders(Sheet sheet, int startRow, int rowCount) {
        List<String> mergedHeaders = new ArrayList<>();
        int maxCols = 0;
        for (int r = startRow; r < startRow + rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row != null) maxCols = Math.max(maxCols, row.getLastCellNum());
        }
        for (int c = 0; c < maxCols; c++) {
            StringBuilder cellBuilder = new StringBuilder();
            for (int r = startRow; r < startRow + rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        String cellValue = getCellValueAsString(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            if (cellBuilder.length() > 0) cellBuilder.append("/");
                            cellBuilder.append(cellValue.trim());
                        }
                    }
                }
            }
            String header = cellBuilder.toString().trim();
            if (header.isEmpty()) {
                header = "_EMPTY_COLUMN_" + c;
            }
            mergedHeaders.add(header);
        }
        return makeUniqueHeaders(mergedHeaders);
    }

    /**
     * 单元格值转字符串（处理各种类型）
     */
    private static String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double num = cell.getNumericCellValue();
                    return (num == (long) num) ? String.valueOf((long) num) : String.valueOf(num);
                }
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            default: return "";
        }
    }

    /**
     * 对可能重复的表头进行唯一化处理（添加 _序号 后缀）
     */
    private static List<String> makeUniqueHeaders(List<String> rawHeaders) {
        List<String> unique = new ArrayList<>();
        Map<String, Integer> counter = new HashMap<>();
        for (String h : rawHeaders) {
            if (h == null) h = "";
            String key = h;
            int count = counter.getOrDefault(key, 0);
            if (count > 0) {
                unique.add(h + "_" + (count + 1));
            } else {
                unique.add(h);
            }
            counter.put(key, count + 1);
        }
        return unique;
    }
}