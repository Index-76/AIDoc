package com.project.aidoc.common.utils;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Word 填充工具类（整合 WordUtils 功能）
 */
public class WordSplicerUtil {

    private static final Logger log = LoggerFactory.getLogger(WordSplicerUtil.class);
    private static final String MISSING_VALUE_MARKER = "<null>";

    /**
     * 填充 Word 模板
     * @param filledFileId 模板副本文件 ID
     * @param mergedTableData 按表格索引分组的行数据
     * @param tableInfo 表格信息（含 tables 列表）
     * @param userId 用户ID
     * @param fileService 文件服务
     * @param templateOriginalName 模板原始文件名（用于生成新文件名）
     * @return 更新后的文件 ID
     */
    public static String fillWordTemplate(String filledFileId, Map<Integer, List<Object[]>> mergedTableData,
                                           Map<String, Object> tableInfo, String userId, FileService fileService,
                                           String templateOriginalName) throws Exception {
        log.info("填充 Word 模板");

        byte[] fileContent = fileService.getFileContent(filledFileId, userId);
        if (fileContent == null) throw new Exception("获取 Word 模板副本内容失败");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent);
             XWPFDocument document = new XWPFDocument(inputStream)) {

            List<XWPFTable> tableList = document.getTables();
            List<Map<String, Object>> tables = (List<Map<String, Object>>) tableInfo.get("tables");

            for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                if (tableIndex >= tableList.size()) {
                    log.warn("表格索引 {} 超出文档实际表格数 {}", tableIndex, tableList.size());
                    continue;
                }

                XWPFTable table = tableList.get(tableIndex);
                List<Object[]> rowDataList = mergedTableData.getOrDefault(tableIndex, new ArrayList<>());
                if (rowDataList.isEmpty()) {
                    log.info("表格 {} 无数据需要填充", tableIndex);
                    continue;
                }

                List<String> originalHeaders = (List<String>) tables.get(tableIndex).get("headers");
                int headerRowCount = (int) tables.get(tableIndex).getOrDefault("headerRowCount", 1);

                // 提取目标表头并唯一化
                List<String> targetHeadersRaw = WordTableExtractorUtil.extractTableHeaders(table, headerRowCount);
                List<String> uniqueTargetHeaders = makeUniqueHeaders(targetHeadersRaw);

                // 构建列映射
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

                int dataStartRow = headerRowCount;
                int existingDataRows = table.getNumberOfRows() - dataStartRow;
                int neededRows = rowDataList.size();

                // 调整行数
                if (neededRows > existingDataRows) {
                    int rowsToAdd = neededRows - existingDataRows;
                    for (int i = 0; i < rowsToAdd; i++) table.createRow();
                    log.info("表格 {} 新增 {} 行", tableIndex, rowsToAdd);
                } else if (neededRows < existingDataRows) {
                    int rowsToRemove = existingDataRows - neededRows;
                    for (int i = 0; i < rowsToRemove; i++) {
                        int lastRowIndex = table.getNumberOfRows() - 1;
                        if (lastRowIndex >= dataStartRow) table.removeRow(lastRowIndex);
                    }
                    log.info("表格 {} 删除 {} 行", tableIndex, rowsToRemove);
                }

                // 填充数据
                for (int i = 0; i < neededRows; i++) {
                    Object[] rowData = rowDataList.get(i);
                    int rowIndex = dataStartRow + i;
                    if (rowIndex >= table.getNumberOfRows()) continue;
                    XWPFTableRow row = table.getRow(rowIndex);
                    List<XWPFTableCell> cells = row.getTableCells();

                    for (int origCol = 0; origCol < rowData.length; origCol++) {
                        int targetCol = colMapping[origCol];
                        if (targetCol == -1 || targetCol >= cells.size()) continue;
                        Object value = rowData[origCol];
                        cells.get(targetCol).setText((value != null && !isMissingValue(value)) ? value.toString() : "");
                    }
                }
                log.info("表格 {} 填充完成，共 {} 行", tableIndex, rowDataList.size());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            byte[] updatedContent = outputStream.toByteArray();

            // 生成新文件名
            String newOriginalName;
            if (templateOriginalName != null) {
                int lastDotIndex = templateOriginalName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    String fileName = templateOriginalName.substring(0, lastDotIndex);
                    String extension = templateOriginalName.substring(lastDotIndex);
                    newOriginalName = fileName + "_filled" + extension;
                } else {
                    newOriginalName = templateOriginalName + "_filled";
                }
            } else {
                newOriginalName = filledFileId + "_filled.docx";
            }

            MultipartFile updatedFile = JsonToExcelWriterUtil.createMultipartFile(
                    filledFileId + "_filled.docx", newOriginalName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", updatedContent
            );
            File savedFile = fileService.saveFile(updatedFile, "temp", userId);
            log.info("Word 文档已保存：{} (ID: {})", savedFile.getFileName(), savedFile.getId());
            return savedFile.getId();
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

    private static boolean isMissingValue(Object val) {
        if (val == null) return true;
        String str = val.toString().trim();
        return str.isEmpty() || MISSING_VALUE_MARKER.equals(str);
    }
}