package com.project.aidoc.common.utils;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件转换工具类
 * 支持将 Word、Excel、Markdown 等格式转换为纯文本
 */
public class ExtractText {

    private static final Logger logger = LoggerFactory.getLogger(ExtractText.class);
    private static final Tika tika = new Tika();

    /**
     * 将文件内容转换为纯文本
     * 
     * @param fileContent 文件内容字节数组
     * @param fileName 文件名 (用于判断文件类型)
     * @return 转换后的纯文本内容
     * @throws Exception 转换过程中可能出现的异常
     */
    public static String convertToText(byte[] fileContent, String fileName) throws Exception {
        if (fileContent == null || fileName == null) {
            throw new IllegalArgumentException("文件内容和文件名不能为空");
        }

        String fileExtension = getFileExtension(fileName);
        
        // 根据文件类型选择转换方式
        switch (fileExtension.toLowerCase()) {
            case "doc":
                return convertDocToText(fileContent);
            case "docx":
                return convertDocxToText(fileContent);
            case "xls":
                return convertXlsToText(fileContent);
            case "xlsx":
                return convertXlsxToText(fileContent);
            case "md":
            case "markdown":
                return new String(fileContent, "UTF-8");
            case "txt":
                // txt 文件直接返回内容
                return new String(fileContent, "UTF-8");
            default:
                // 使用 Tika 自动检测并转换
                return convertWithTika(fileContent);
        }
    }

    /**
     * 转换 Word (.docx) 文件为文本
     */
    private static String convertDocxToText(byte[] fileContent) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();
        } catch (TikaException e) {
            logger.warn("Tika 解析 DOCX 文件失败：{}", e.getMessage());
            // 降级处理：尝试使用基础 POI 库直接读取
            return convertDocxWithBasicPOI(fileContent);
        } catch (Exception e) {
            logger.error("未知错误导致 DOCX 文件转换失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 使用基础 POI 库直接读取 DOCX 文件内容（降级方案）
     */
    private static String convertDocxWithBasicPOI(byte[] fileContent) throws Exception {
        StringBuilder textContent = new StringBuilder();
        
        try (InputStream inputStream = new ByteArrayInputStream(fileContent);
             org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream)) {
            
            // 提取段落
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    textContent.append(text).append("\n");
                }
            }
            
            // 提取表格内容
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : document.getTables()) {
                textContent.append("[表格内容]\n");
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        rowText.append(cell.getText()).append("\t");
                    }
                    textContent.append(rowText.toString().trim()).append("\n");
                }
                textContent.append("\n");
            }
            
            // 提取页眉页脚（如果存在）
            if (document.getHeaderList() != null && !document.getHeaderList().isEmpty()) {
                textContent.append("\n[页眉内容]\n");
                for (org.apache.poi.xwpf.usermodel.XWPFHeader header : document.getHeaderList()) {
                    textContent.append(header.getText()).append("\n");
                }
            }
            
            if (document.getFooterList() != null && !document.getFooterList().isEmpty()) {
                textContent.append("\n[页脚内容]\n");
                for (org.apache.poi.xwpf.usermodel.XWPFFooter footer : document.getFooterList()) {
                    for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : footer.getParagraphs()) {
                        textContent.append(paragraph.getText()).append("\n");
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("基础 POI 方式读取 DOCX 也失败：{}", e.getMessage());
            throw new Exception("DOCX 文件无法解析：" + e.getMessage(), e);
        }
        
        return textContent.toString();
    }

    /**
     * 转换 Word 97-2003 (.doc) 文件为文本
     */
    private static String convertDocToText(byte[] fileContent) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();
        } catch (TikaException e) {
            logger.warn("Tika 解析 DOC 文件失败：{}", e.getMessage());
            throw new Exception("DOC 文件解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 转换 Excel 97-2003 (.xls) 文件为文本
     */
    private static String convertXlsToText(byte[] fileContent) throws Exception {
        StringBuilder textContent = new StringBuilder();
        
        try (InputStream inputStream = new ByteArrayInputStream(fileContent);
             Workbook workbook = new HSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                textContent.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n\n");
                
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        cell.setCellType(CellType.STRING);
                        textContent.append(cell.getStringCellValue()).append("\t");
                    }
                    textContent.append("\n");
                }
                textContent.append("\n");
            }
        }
        
        return textContent.toString();
    }

    /**
     * 转换 Excel (.xlsx) 文件为文本
     */
    private static String convertXlsxToText(byte[] fileContent) throws Exception {
        StringBuilder textContent = new StringBuilder();
        
        try (InputStream inputStream = new ByteArrayInputStream(fileContent);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                textContent.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n\n");
                
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        cell.setCellType(CellType.STRING);
                        textContent.append(cell.getStringCellValue()).append("\t");
                    }
                    textContent.append("\n");
                }
                textContent.append("\n");
            }
        }
        
        return textContent.toString();
    }

    /**
     * 使用 Tika 转换未知格式文件为文本
     */
    private static String convertWithTika(byte[] fileContent) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();
        } catch (TikaException e) {
            logger.warn("Tika 解析未知格式文件失败：{}", e.getMessage());
            throw new Exception("文件格式无法识别：" + e.getMessage(), e);
        }
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
