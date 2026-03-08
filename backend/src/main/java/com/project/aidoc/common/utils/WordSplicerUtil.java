package com.project.aidoc.common.utils;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Word 文档填充工具类
 * 用于将合并后的表格数据填充到 Word 模板的表格中
 */
public class WordSplicerUtil {

    private static final Logger log = LoggerFactory.getLogger(WordSplicerUtil.class);
    private static final String MISSING_VALUE_MARKER = "<null>";

    /**
     * 填充 Word 模板
     * @param filledFileId 待填充的 Word 文件 ID
     * @param mergedTableData 合并后的表格数据，key 为表格索引，value 为行数据列表
     * @param tableInfo 表格信息（含表头、表头行数等）
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @param templateOriginalName 模板原始文件名（用于生成新文件名）
     * @return 填充后保存的新文件 ID
     * @throws Exception 填充失败时抛出
     */
    public static String fillWordTemplate(String filledFileId, Map<Integer, List<Object[]>> mergedTableData,
                                          Map<String, Object> tableInfo, Long userId, FileService fileService,
                                          String templateOriginalName) throws Exception {
        log.info("填充 Word 模板");

        byte[] fileContent = fileService.getFileContent(filledFileId, userId);
        if (fileContent == null) {
            throw new Exception("获取 Word 模板副本内容失败");
        }

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
                List<Object[]> rowDataList = mergedTableData.getOrDefault(tableIndex, new java.util.ArrayList<>());
                List<String> headers = (List<String>) ((Map<String, Object>) tables.get(tableIndex)).get("headers");

                log.info("开始填充表格 {}，表头数：{}，数据行数：{}", tableIndex, headers.size(), rowDataList.size());

                // 从表格信息中获取表头行数
                int headerRowCount = (int) ((Map<String, Object>) tables.get(tableIndex))
                        .getOrDefault("headerRowCount", 1);
                int dataStartRow = headerRowCount;

                int existingDataRows = table.getNumberOfRows() - dataStartRow;
                int neededRows = rowDataList.size();

                if (neededRows > existingDataRows) {
                    int rowsToAdd = neededRows - existingDataRows;
                    for (int i = 0; i < rowsToAdd; i++) {
                        table.createRow();
                    }
                    log.info("表格 {} 新增 {} 行", tableIndex, rowsToAdd);
                } else if (neededRows < existingDataRows) {
                    int rowsToRemove = existingDataRows - neededRows;
                    for (int i = 0; i < rowsToRemove; i++) {
                        int lastRowIndex = table.getNumberOfRows() - 1;
                        if (lastRowIndex >= dataStartRow) {
                            table.removeRow(lastRowIndex);
                        }
                    }
                    log.info("表格 {} 删除 {} 行", tableIndex, rowsToRemove);
                }

                for (int i = 0; i < rowDataList.size(); i++) {
                    Object[] rowData = rowDataList.get(i);
                    int rowIndex = dataStartRow + i;

                    if (rowIndex >= table.getNumberOfRows()) {
                        log.warn("行索引 {} 超出表格行数 {}", rowIndex, table.getNumberOfRows());
                        continue;
                    }

                    XWPFTableRow row = table.getRow(rowIndex);
                    List<XWPFTableCell> cells = row.getTableCells();

                    for (int col = 0; col < rowData.length; col++) {
                        Object value = rowData[col];
                        if (col >= cells.size()) {
                            log.warn("行 {} 列 {} 超出表格实际列数 {}，跳过该列", rowIndex, col, cells.size());
                            continue;
                        }
                        XWPFTableCell cell = cells.get(col);
                        if (value != null && !isMissingValue(value)) {
                            cell.setText(value.toString());
                        } else {
                            cell.setText("");
                        }
                    }
                }
                log.info("表格 {} 填充完成", tableIndex);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            byte[] updatedContent = outputStream.toByteArray();

            // 构造新文件名
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
                    filledFileId + "_filled.docx",
                    newOriginalName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    updatedContent
            );

            File savedFile = fileService.saveFile(updatedFile, "temp", userId);
            log.info("Word 文档已保存：{} (ID: {})", savedFile.getFileName(), savedFile.getId());

            return savedFile.getId();

        } catch (Exception e) {
            log.error("填充 Word 模板失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    private static boolean isMissingValue(Object val) {
        if (val == null) return true;
        String str = val.toString().trim();
        return str.isEmpty() || MISSING_VALUE_MARKER.equals(str);
    }
}
