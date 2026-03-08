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
 * Word 表格提取工具类
 * 用于从 Word 文档中提取表格信息（表头、上下文等），并保存为 JSON 临时文件
 */
public class WordTableExtractorUtil {

    private static final Logger log = LoggerFactory.getLogger(WordTableExtractorUtil.class);

    /**
     * 提取 Word 模板中的表格信息（表头及上下文）
     * @param templateFile 模板文件实体
     * @param userId 用户 ID
     * @param fileService 文件服务
     * @return 包含表格信息的 Map，结构同原 TxtToWord.extractTableInfo 返回值
     * @throws Exception 提取失败时抛出
     */
    public static Map<String, Object> extractTables(File templateFile, Long userId, FileService fileService) throws Exception {
        log.info("提取 Word 模板表格信息");

        byte[] templateContent = fileService.getFileContent(templateFile.getId(), userId);
        if (templateContent == null) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(templateContent);
             XWPFDocument document = new XWPFDocument(inputStream)) {

            // 初始化结果对象
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", templateFile.getId());

            List<Map<String, Object>> tables = new ArrayList<>();
            result.put("tables", tables);

            // 获取文档元素列表用于精确定位
            List<IBodyElement> elements = document.getBodyElements();
            List<Integer> tableIndices = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).getElementType() == BodyElementType.TABLE) {
                    tableIndices.add(i);
                }
            }

            // 遍历所有表格
            List<XWPFTable> tableList = document.getTables();
            for (int i = 0; i < tableList.size(); i++) {
                XWPFTable table = tableList.get(i);
                int tableElemIndex = (i < tableIndices.size()) ? tableIndices.get(i) : -1;

                // 提取上文（当前表格之前的段落）
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
                    if (description1.length() > 500) description1 = new StringBuilder(description1.substring(0, 500));
                }

                // 提取下文（当前表格之后的段落）
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
                if (description2.length() > 500) description2 = new StringBuilder(description2.substring(0, 500));

                // 提取表头（识别主键列标记 [PK]）和计算表头行数
                List<String> headers = new ArrayList<>();
                String primaryKeyHeader = null;
                int headerRowCount = 0;

                // 计算重复标题行数（Word 表格属性）
                for (int r = 0; r < table.getNumberOfRows(); r++) {
                    if (table.getRow(r).isRepeatHeader()) {
                        headerRowCount++;
                    } else {
                        break;
                    }
                }
                if (headerRowCount == 0) {
                    headerRowCount = 1; // 默认至少一行表头
                }

                if (table.getNumberOfRows() > 0) {
                    XWPFTableRow firstRow = table.getRow(0);
                    List<XWPFTableCell> cells = firstRow.getTableCells();
                    for (XWPFTableCell cell : cells) {
                        String cellText = cell.getText().trim();
                        if (cellText.contains("[PK]")) {
                            String cleaned = cellText.replace("[PK]", "").trim();
                            if (!cleaned.isEmpty() && !headers.contains(cleaned)) {
                                headers.add(cleaned);
                            }
                            // 只取第一个出现的 [PK] 列作为主键
                            if (primaryKeyHeader == null) {
                                primaryKeyHeader = cleaned;
                            } else {
                                log.warn("表格 {} 检测到多个主键标记，已忽略多余的标记：'{}'", i, cleaned);
                            }
                        } else {
                            if (!cellText.isEmpty() && !headers.contains(cellText)) {
                                headers.add(cellText);
                            }
                        }
                    }
                }

                // 构建表格信息对象
                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("tableIndex", i);
                tableInfo.put("sheetDiscription1", description1.toString());
                tableInfo.put("sheetDiscription2", description2.toString());
                tableInfo.put("headers", headers);
                tableInfo.put("primaryKeyHeader", primaryKeyHeader);
                tableInfo.put("headerRowCount", headerRowCount);
                tables.add(tableInfo);

                log.debug("提取表格 {} 信息：表头数={}, 表头行数={}, 上文长度={}, 下文长度={}, 主键={}",
                        i, headers.size(), headerRowCount, description1.length(), description2.length(), primaryKeyHeader);
            }

            result.put("tableNum", tableList.size());

            // 保存为 JSON 文件到临时区
            ObjectMapper mapper = new ObjectMapper();
            String jsonContent = mapper.writeValueAsString(result);

            String fileName = templateFile.getId() + "_tables.json";
            MultipartFile tableInfoFile = JsonToExcelWriterUtil.createMultipartFile(
                    fileName, fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8)
            );

            File savedFile = fileService.saveFile(tableInfoFile, "temp", userId);
            log.info("表格信息已保存到临时文件：{} (ID: {})", fileName, savedFile.getId());

            // 更新 fileId 为实际保存的文件 ID
            result.put("fileId", savedFile.getId());

            return result;

        } catch (Exception e) {
            log.error("提取 Word 模板表格信息失败：{}", e.getMessage(), e);
            return null;
        }
    }
}
