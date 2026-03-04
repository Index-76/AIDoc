package com.project.aidoc.common.utils;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.Tika;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 文件转换工具类
 * 支持将 Word、Excel、Markdown 等格式转换为纯文本
 */
public class ExtractText {

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
