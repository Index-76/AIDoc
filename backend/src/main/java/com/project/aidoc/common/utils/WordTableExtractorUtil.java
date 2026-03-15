package com.project.aidoc.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Word 表格提取工具类（整合 WordUtils 功能）
 */
public class WordTableExtractorUtil {

    private static final Logger log = LoggerFactory.getLogger(WordTableExtractorUtil.class);

    /**
     * 提取 Word 模板中的表格信息（默认自动检测表头行数）
     */
    public static Map<String, Object> extractTables(File templateFile, String userId, FileService fileService) throws Exception {
        return extractTables(templateFile, userId, fileService, 0, null);
    }

    /**
     * 提取 Word 模板中的表格信息（完整参数）
     * @param templateFile 模板文件
     * @param userId 用户ID
     * @param fileService 文件服务
     * @param headerRowCount 指定表头行数（<=0 则自动检测）
     * @param primaryKeyHeader 可选的主键列名
     * @return 包含 tables 列表的 Map，同时保存 JSON 文件到临时区
     */
    public static Map<String, Object> extractTables(File templateFile, String userId, FileService fileService,
                                                     int headerRowCount, String primaryKeyHeader) throws Exception {
        log.info("提取 Word 模板表格信息，指定表头行数：{}，主键列：{}", headerRowCount > 0 ? headerRowCount : "自动检测", primaryKeyHeader);

        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) return null;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(templateContent);
             XWPFDocument document = new XWPFDocument(inputStream)) {

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", templateFile.getId());

            List<Map<String, Object>> tables = new ArrayList<>();
            result.put("tables", tables);

            List<IBodyElement> elements = document.getBodyElements();
            List<Integer> tableIndices = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).getElementType() == BodyElementType.TABLE) tableIndices.add(i);
            }

            List<XWPFTable> tableList = document.getTables();
            for (int i = 0; i < tableList.size(); i++) {
                XWPFTable table = tableList.get(i);
                int tableElemIndex = (i < tableIndices.size()) ? tableIndices.get(i) : -1;

                // 提取上文
                StringBuilder description1 = new StringBuilder();
                if (tableElemIndex > 0) {
                    int prevTableIndex = (i > 0) ? tableIndices.get(i - 1) : -1;
                    int start = (prevTableIndex >= 0) ? prevTableIndex + 1 : 0;
                    for (int j = start; j < tableElemIndex; j++) {
                        if (elements.get(j).getElementType() == BodyElementType.PARAGRAPH) {
                            String text = ((XWPFParagraph) elements.get(j)).getText().trim();
                            if (!text.isEmpty()) {
                                if (description1.length() > 0) description1.append("\n");
                                description1.append(text);
                            }
                        }
                    }
                    if (description1.length() > 500) description1.setLength(500);
                }

                // 提取下文
                StringBuilder description2 = new StringBuilder();
                int nextTableIndex = (i + 1 < tableIndices.size()) ? tableIndices.get(i + 1) : elements.size();
                for (int j = tableElemIndex + 1; j < nextTableIndex; j++) {
                    if (elements.get(j).getElementType() == BodyElementType.PARAGRAPH) {
                        String text = ((XWPFParagraph) elements.get(j)).getText().trim();
                        if (!text.isEmpty()) {
                            if (description2.length() > 0) description2.append("\n");
                            description2.append(text);
                        }
                    }
                }
                if (description2.length() > 500) description2.setLength(500);

                // 确定表头行数
                int detectedHeaderRowCount = headerRowCount;
                if (detectedHeaderRowCount <= 0) {
                    detectedHeaderRowCount = detectHeaderRows(table);
                }
                if (detectedHeaderRowCount <= 0) detectedHeaderRowCount = 1;

                // 提取合并表头
                List<String> headers = extractTableHeaders(table, detectedHeaderRowCount);
                // 检测主键标记（如果表头中带有 [PK]）
                String detectedPrimaryKey = null;
                if (primaryKeyHeader == null && !headers.isEmpty()) {
                    for (int r = 0; r < Math.min(detectedHeaderRowCount, table.getNumberOfRows()); r++) {
                        XWPFTableRow row = table.getRow(r);
                        if (row == null) continue;
                        List<XWPFTableCell> cells = row.getTableCells();
                        for (int c = 0; c < cells.size(); c++) {
                            String cellText = cells.get(c).getText().trim();
                            if (cellText.contains("[PK]")) {
                                String cleaned = cellText.replace("[PK]", "").trim();
                                if (c < headers.size()) {
                                    // 使用唯一化后的表头作为主键名
                                    detectedPrimaryKey = headers.get(c);
                                    break;
                                }
                            }
                        }
                        if (detectedPrimaryKey != null) break;
                    }
                }

                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("tableIndex", i);
                tableInfo.put("sheetDiscription1", description1.toString());
                tableInfo.put("sheetDiscription2", description2.toString());
                tableInfo.put("headers", headers);
                tableInfo.put("primaryKeyHeader", primaryKeyHeader != null ? primaryKeyHeader : detectedPrimaryKey);
                tableInfo.put("headerRowCount", detectedHeaderRowCount);
                tables.add(tableInfo);

                log.debug("提取表格 {} 信息：表头数={}, 表头行数={}, 主键={}", i, headers.size(), detectedHeaderRowCount, tableInfo.get("primaryKeyHeader"));
            }

            result.put("tableNum", tableList.size());

            // 保存为 JSON 文件
            ObjectMapper mapper = new ObjectMapper();
            String jsonContent = mapper.writeValueAsString(result);
            String fileName = templateFile.getId() + "_tables.json";
            MultipartFile tableInfoFile = JsonToExcelWriterUtil.createMultipartFile(
                    fileName, fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8)
            );
            File savedFile = fileService.saveFile(tableInfoFile, "temp", userId);
            log.info("表格信息已保存到临时文件：{} (ID: {})", fileName, savedFile.getId());
            result.put("fileId", savedFile.getId());

            return result;
        }
    }

    /**
     * 从 Word 表格提取合并后的表头（支持多行）
     */
    public static List<String> extractTableHeaders(XWPFTable table, int headerRowCount) {
        List<String> headers = new ArrayList<>();
        int maxCols = 0;
        for (int r = 0; r < headerRowCount; r++) {
            XWPFTableRow row = table.getRow(r);
            if (row != null) maxCols = Math.max(maxCols, row.getTableCells().size());
        }
        for (int c = 0; c < maxCols; c++) {
            StringBuilder builder = new StringBuilder();
            for (int r = 0; r < headerRowCount; r++) {
                XWPFTableRow row = table.getRow(r);
                if (row != null && c < row.getTableCells().size()) {
                    XWPFTableCell cell = row.getCell(c);
                    String text = cell.getText().trim();
                    if (!text.isEmpty()) {
                        if (builder.length() > 0) builder.append("/");
                        builder.append(text);
                    }
                }
            }
            String header = builder.toString().trim();
            if (header.isEmpty()) header = "_EMPTY_COLUMN_" + c;
            headers.add(header);
        }
        return makeUniqueHeaders(headers);
    }

    /**
     * 自动检测表头行数（简单启发式）
     */
    private static int detectHeaderRows(XWPFTable table) {
        if (table.getNumberOfRows() > 1) {
            XWPFTableRow row0 = table.getRow(0);
            XWPFTableRow row1 = table.getRow(1);
            if (row0 != null && row1 != null) {
                int cols0 = row0.getTableCells().size();
                int cols1 = row1.getTableCells().size();
                if (cols0 < cols1) return 1;
            }
        }
        return 0;
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
}