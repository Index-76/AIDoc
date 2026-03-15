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
import java.util.*;

/**
 * JSON 数据写入 Excel 工具类（整合 ExcelUtils 功能）
 */
public class JsonToExcelWriterUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonToExcelWriterUtil.class);
    private static final String MISSING_VALUE_MARKER = "<null>";

    /**
     * 填充 Excel 模板
     * @param filledFileId 模板副本文件 ID
     * @param mergedData 按工作表名分组的行数据
     * @param headerInfo 表头信息（包含 sheets 列表）
     * @param userId 用户ID
     * @param fileService 文件服务
     * @return 更新后的文件 ID
     */
    public static String fillExcelTemplate(String filledFileId, Map<String, List<Object[]>> mergedData,
                                            Map<String, Object> headerInfo, String userId, FileService fileService) throws Exception {
        log.info("填充 Excel 模板");

        byte[] templateContent = fileService.getFileContent(filledFileId, userId);
        if (templateContent == null) throw new Exception("获取模板副本内容失败");

        byte[] outputContent;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templateContent))) {
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");

            for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
                Map<String, Object> sheetInfo = sheets.get(sheetIndex);
                String sheetName = (String) sheetInfo.get("sheetName");
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) sheet = workbook.createSheet(sheetName);

                List<Object[]> rows = mergedData.get(sheetName);
                if (rows == null || rows.isEmpty()) {
                    log.warn("工作表 '{}' 没有数据可写入", sheetName);
                    continue;
                }

                // 获取该工作表的表头信息
                int headerRowIndex = (int) sheetInfo.getOrDefault("headerRowIndex", headerInfo.get("headerRowIndex"));
                int headerRowCount = (int) sheetInfo.getOrDefault("headerRowCount", headerInfo.get("headerRowCount"));
                int dataStartRow = headerRowIndex + headerRowCount;

                // 从模板工作表提取目标表头（可能重复）
                List<String> targetHeadersRaw = extractMergedHeaders(sheet, headerRowIndex, headerRowCount);
                List<String> uniqueTargetHeaders = makeUniqueHeaders(targetHeadersRaw);
                List<String> originalHeaders = (List<String>) sheetInfo.get("headers");

                // 构建列名到目标列索引的映射（忽略大小写，完整匹配）
                int[] colMapping = new int[originalHeaders.size()];
                Arrays.fill(colMapping, -1);
                for (int i = 0; i < originalHeaders.size(); i++) {
                    String colName = originalHeaders.get(i).trim();
                    for (int j = 0; j < uniqueTargetHeaders.size(); j++) {
                        if (uniqueTargetHeaders.get(j).trim().equalsIgnoreCase(colName)) {
                            colMapping[i] = j;
                            break;
                        }
                    }
                }

                // 调整行数：清空多余行或创建新行
                int lastRowNum = sheet.getLastRowNum();
                List<Integer> existingRowIndices = new ArrayList<>();
                for (int r = dataStartRow; r <= lastRowNum; r++) {
                    if (sheet.getRow(r) != null) existingRowIndices.add(r);
                }
                int existingDataRows = existingRowIndices.size();
                int neededRows = rows.size();

                if (neededRows > existingDataRows) {
                    int lastExistingRow = existingRowIndices.isEmpty() ? dataStartRow - 1 : existingRowIndices.get(existingRowIndices.size() - 1);
                    for (int i = 0; i < neededRows - existingDataRows; i++) {
                        sheet.createRow(lastExistingRow + 1 + i);
                    }
                    log.info("工作表 '{}' 新增 {} 行", sheetName, neededRows - existingDataRows);
                } else if (neededRows < existingDataRows) {
                    for (int i = neededRows; i < existingDataRows; i++) {
                        int rowIndex = existingRowIndices.get(i);
                        Row row = sheet.getRow(rowIndex);
                        if (row != null) {
                            for (int c = 0; c < row.getLastCellNum(); c++) {
                                Cell cell = row.getCell(c);
                                if (cell != null) cell.setBlank();
                            }
                        }
                    }
                    log.info("工作表 '{}' 清空 {} 行", sheetName, existingDataRows - neededRows);
                }

                // 填充数据
                for (int i = 0; i < neededRows; i++) {
                    Row row = sheet.getRow(dataStartRow + i);
                    if (row == null) row = sheet.createRow(dataStartRow + i);
                    Object[] rowData = rows.get(i);
                    for (int colIdx = 0; colIdx < rowData.length; colIdx++) {
                        int targetCol = colMapping[colIdx];
                        if (targetCol == -1) continue;
                        Cell cell = row.getCell(targetCol);
                        if (cell == null) cell = row.createCell(targetCol);
                        Object value = rowData[colIdx];
                        if (value != null && !isMissingValue(value)) {
                            setCellValue(cell, value);
                        } else {
                            cell.setBlank();
                        }
                    }
                }
                log.info("工作表 '{}' 写入完成，共 {} 行", sheetName, rows.size());
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            outputContent = baos.toByteArray();
        }

        // 保存新文件，删除旧副本
        File filledFile = fileService.getFileById(filledFileId, userId);
        if (filledFile == null) throw new Exception("找不到模板副本文件：" + filledFileId);

        String templateOriginalName = (String) headerInfo.get("templateOriginalName");
        if (templateOriginalName == null) templateOriginalName = filledFile.getOriginalName();

        int lastDotIndex = templateOriginalName.lastIndexOf('.');
        String newOriginalName;
        if (lastDotIndex > 0) {
            String fileName = templateOriginalName.substring(0, lastDotIndex);
            String extension = templateOriginalName.substring(lastDotIndex);
            newOriginalName = fileName + "_filled" + extension;
        } else {
            newOriginalName = templateOriginalName + "_filled";
        }

        MultipartFile updatedFile = createMultipartFile(
                filledFile.getFileName(), newOriginalName, filledFile.getContentType(), outputContent
        );
        File newFile = fileService.saveFile(updatedFile, "temp", userId);
        fileService.deleteFileById(filledFileId, userId);
        log.info("Excel 文件已更新，原文件 ID: {}, 新文件 ID: {}", filledFileId, newFile.getId());
        return newFile.getId();
    }

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
            if (header.isEmpty()) header = "_EMPTY_COLUMN_" + c;
            mergedHeaders.add(header);
        }
        return mergedHeaders;
    }

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

    private static void setCellValue(Cell cell, Object value) {
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static boolean isMissingValue(Object val) {
        if (val == null) return true;
        String str = val.toString().trim();
        return str.isEmpty() || MISSING_VALUE_MARKER.equals(str);
    }

    /**
     * 通用 MultipartFile 创建工具
     */
    public static MultipartFile createMultipartFile(String name, String originalFilename,
                                                      String contentType, byte[] content) {
        return new MultipartFile() {
            @Override public String getName() { return name; }
            @Override public String getOriginalFilename() { return originalFilename; }
            @Override public String getContentType() { return contentType; }
            @Override public boolean isEmpty() { return content == null || content.length == 0; }
            @Override public long getSize() { return content != null ? content.length : 0; }
            @Override public byte[] getBytes() { return content; }
            @Override public ByteArrayInputStream getInputStream() { return new ByteArrayInputStream(content); }
            @Override public void transferTo(java.io.File dest) { /* 无需实现 */ }
        };
    }
}