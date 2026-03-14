package com.project.aidoc.common.tools;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.project.aidoc.entity.File;
import com.project.aidoc.repository.FileRepository;
import com.project.aidoc.service.FileService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 格式转换工具类
 * 用于调用对应的 utils 进行文件格式转换
 */
@Component
public class FormatConverterTool {

    private static final Logger logger = LoggerFactory.getLogger(FormatConverterTool.class);

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    /**
     * 转换文件格式
     * @param conversionRequirements 转换要求参数
     * @param filePath 待转换的文件路径（实际是文件 ID）
     * @return 转换结果 Map
     */
    public Map<String, Object> convertFormat(String conversionRequirements, String filePath) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从消息中提取目标格式和源文件信息
            String targetFormat = extractTargetFormat(conversionRequirements);
            String sourceFileId = extractSourceFileId(filePath);
            
            if (sourceFileId == null || sourceFileId.isEmpty()) {
                result.put("status", "failed");
                result.put("message", "未找到源文件，请指定要转换的文件");
                return result;
            }
            
            if (targetFormat == null || targetFormat.isEmpty()) {
                result.put("status", "failed");
                result.put("message", "未指定目标格式，请说明要转换为什么格式");
                return result;
            }
            
            // 获取源文件信息
            File sourceFile = fileRepository.findById(sourceFileId).orElse(null);
            if (sourceFile == null) {
                result.put("status", "failed");
                result.put("message", "源文件不存在");
                return result;
            }
            
            // 获取文件内容
            byte[] fileContent = getFileContent(sourceFileId);
            if (fileContent == null || fileContent.length == 0) {
                result.put("status", "failed");
                result.put("message", "无法读取源文件内容");
                return result;
            }
            
            // 执行格式转换
            String sourceExtension = getFileExtension(sourceFile.getOriginalName());
            byte[] convertedContent = performConversion(fileContent, sourceExtension, targetFormat);
            
            if (convertedContent == null) {
                result.put("status", "failed");
                result.put("message", "格式转换失败，不支持的转换格式或转换过程出错");
                return result;
            }
            
            // 保存转换结果到 result 区
            String resultFileId = saveConvertedFileToResult(
                convertedContent, 
                sourceFile.getOriginalName(), 
                targetFormat, 
                sourceFile.getUserId()
            );
            
            result.put("status", "success");
            result.put("message", "格式转换成功，文件已保存到结果区");
            result.put("resultFileId", resultFileId);
            result.put("sourceFileName", sourceFile.getOriginalName());
            result.put("targetFormat", targetFormat.toUpperCase());
            
        } catch (Exception e) {
            logger.error("格式转换失败", e);
            result.put("status", "failed");
            result.put("message", "格式转换失败：" + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 从转换要求中提取目标格式
     */
    private String extractTargetFormat(String requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return null;
        }
        
        String lowerReq = requirements.toLowerCase();
        
        if (lowerReq.contains("pdf")) {
            return "pdf";
        } else if (lowerReq.contains("word") || lowerReq.contains("docx") || lowerReq.contains("doc")) {
            return "docx";
        } else if (lowerReq.contains("excel") || lowerReq.contains("xlsx") || lowerReq.contains("xls")) {
            return "xlsx";
        } else if (lowerReq.contains("txt") || lowerReq.contains("text")) {
            return "txt";
        } else if (lowerReq.contains("md") || lowerReq.contains("markdown")) {
            return "md";
        }
        
        return null;
    }
    
    /**
     * 从文件路径中提取文件 ID
     */
    private String extractSourceFileId(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        // 如果 filePath 已经是 ObjectId 格式，直接返回
        if (filePath.matches("[0-9a-f]{24}")) {
            return filePath;
        }
        
        // 尝试从路径中提取文件名或 ID
        String[] parts = filePath.split("[/\\\\]");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // 提取可能的 ID 部分
            if (lastPart.matches("[0-9a-f]{24}.*")) {
                return lastPart.substring(0, 24);
            }
        }
        
        return filePath;
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
    
    /**
     * 从 MongoDB 获取文件内容
     */
    private byte[] getFileContent(String fileId) throws IOException {
        Query query = new Query(Criteria.where("_id").is(fileId));
        GridFSFile gridFSFile = gridFsTemplate.findOne(query);
        
        if (gridFSFile == null) {
            return null;
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        gridFsTemplate.getResource(gridFSFile).getInputStream().transferTo(outputStream);
        return outputStream.toByteArray();
    }
    
    /**
     * 执行格式转换
     */
    private byte[] performConversion(byte[] fileContent, String sourceExtension, String targetFormat) {
        try {
            // PDF 转 Word
            if ("pdf".equals(sourceExtension) && "docx".equals(targetFormat)) {
                return convertPdfToWord(fileContent);
            }
            // Word 转 PDF
            else if (("docx".equals(sourceExtension) || "doc".equals(sourceExtension)) && "pdf".equals(targetFormat)) {
                return convertWordToPdf(fileContent);
            }
            // Excel 转 PDF
            else if (("xlsx".equals(sourceExtension) || "xls".equals(sourceExtension)) && "pdf".equals(targetFormat)) {
                return convertExcelToPdf(fileContent);
            }
            // Markdown/TXT 转 PDF
            else if (("md".equals(sourceExtension) || "markdown".equals(sourceExtension) || "txt".equals(sourceExtension)) && "pdf".equals(targetFormat)) {
                return convertTextToPdf(fileContent, sourceExtension);
            }
            // 其他转换暂不支持
            else {
                logger.warn("暂不支持的转换格式：{} -> {}", sourceExtension, targetFormat);
                return null;
            }
        } catch (Exception e) {
            logger.error("格式转换失败：{} -> {}", sourceExtension, targetFormat, e);
            return null;
        }
    }
    
    /**
     * PDF 转 Word（使用 Tika 提取文本后创建 DOCX）
     */
    private byte[] convertPdfToWord(byte[] pdfContent) throws Exception {
        try {
            // 使用 PDFBox 提取 PDF 文本内容
            String textContent = extractTextFromPdf(pdfContent);
            
            // 使用 Apache POI 创建 DOCX 文件
            return createDocxFromText(textContent);
            
        } catch (Exception e) {
            logger.error("PDF 转 Word 失败", e);
            throw e;
        }
    }
    
    /**
     * 从 PDF 中提取文本内容
     */
    private String extractTextFromPdf(byte[] pdfContent) throws Exception {
        // 使用 PDFBox 提取文本
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfContent)) {
            org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(inputStream);
            org.apache.pdfbox.text.PDFTextStripper textStripper = new org.apache.pdfbox.text.PDFTextStripper();
            return textStripper.getText(document);
        }
    }
    
    /**
     * 从文本创建 DOCX 文件
     */
    private byte[] createDocxFromText(String textContent) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            // 添加段落
            String[] paragraphs = textContent.split("\n");
            for (String paragraph : paragraphs) {
                if (!paragraph.trim().isEmpty()) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph para = document.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun run = para.createRun();
                    run.setText(paragraph);
                }
            }
            
            document.write(baos);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Word 转 PDF（使用 PDFBox）
     */
    private byte[] convertWordToPdf(byte[] wordContent) throws Exception {
        try {
            // 首先从 Word 提取文本
            String textContent = extractTextFromWord(wordContent);
            
            // 然后创建 PDF
            return createPdfFromText(textContent, "Word 文档");
            
        } catch (Exception e) {
            logger.error("Word 转 PDF 失败", e);
            throw e;
        }
    }
    
    /**
     * 从 Word 文档中提取文本
     */
    private String extractTextFromWord(byte[] wordContent) throws Exception {
        StringBuilder textContent = new StringBuilder();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(wordContent);
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
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText());
                    }
                    textContent.append(String.join("\t", cells)).append("\n");
                }
                textContent.append("\n");
            }
        }
        
        return textContent.toString();
    }
    
    /**
     * Excel 转 PDF（使用 POI 读取后创建 PDF）
     */
    private byte[] convertExcelToPdf(byte[] excelContent) throws Exception {
        try {
            // 从 Excel 提取内容
            String textContent = extractTextFromExcel(excelContent);
            
            // 创建 PDF
            return createPdfFromText(textContent, "Excel 表格");
            
        } catch (Exception e) {
            logger.error("Excel 转 PDF 失败", e);
            throw e;
        }
    }
    
    /**
     * 从 Excel 中提取文本内容
     */
    private String extractTextFromExcel(byte[] excelContent) throws Exception {
        StringBuilder textContent = new StringBuilder();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(excelContent);
             Workbook workbook = getWorkbook(inputStream, excelContent)) {
            
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                textContent.append("=== Sheet ").append(sheet.getSheetName()).append(" ===\n\n");
                
                for (Row row : sheet) {
                    StringBuilder rowContent = new StringBuilder();
                    for (Cell cell : row) {
                        rowContent.append(getCellStringValue(cell)).append("\t");
                    }
                    textContent.append(rowContent.toString().trim()).append("\n");
                }
                textContent.append("\n");
            }
        }
        
        return textContent.toString();
    }
    
    /**
     * 获取 Workbook 实例
     */
    private Workbook getWorkbook(ByteArrayInputStream inputStream, byte[] content) throws Exception {
        // 根据文件签名判断是 XLS 还是 XLSX
        if (content.length > 8 && 
            (content[0] == (byte) 0x50 && content[1] == (byte) 0x4B)) { // XLSX (ZIP 格式)
            return new XSSFWorkbook(inputStream);
        } else {
            return new HSSFWorkbook(inputStream);
        }
    }
    
    /**
     * 将 Cell 转换为字符串
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * 文本/Markdown 转 PDF
     */
    private byte[] convertTextToPdf(byte[] textContent, String sourceExtension) throws Exception {
        String textContentStr = new String(textContent, "UTF-8");
        
        // 如果是 Markdown，进行简单的格式处理
        if ("md".equals(sourceExtension) || "markdown".equals(sourceExtension)) {
            textContentStr = processMarkdown(textContentStr);
        }
        
        return createPdfFromText(textContentStr, "文本文档");
    }
    
    /**
     * 简单的 Markdown 处理（提取标题等）
     */
    private String processMarkdown(String markdownContent) {
        StringBuilder processed = new StringBuilder();
        String[] lines = markdownContent.split("\n");
        
        for (String line : lines) {
            // 移除 Markdown 标记，转换为纯文本格式
            line = line.replaceAll("^#{1,6}\\s*", ""); // 移除标题标记
            line = line.replaceAll("\\*\\*(.+?)\\*\\*", "$1"); // 移除粗体标记
            line = line.replaceAll("_(.+?)_", "$1"); // 移除斜体标记
            line = line.replaceAll("`(.+?)`", "$1"); // 移除代码标记
            
            if (!line.trim().isEmpty()) {
                processed.append(line).append("\n");
            }
        }
        
        return processed.toString();
    }
    
    /**
     * 使用 OpenPDF 创建PDF文档（支持中文）
     */
    private byte[] createPdfFromText(String textContent, String title) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            // 创建 Document 和 Writer
            com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4);
            com.lowagie.text.pdf.PdfWriter writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, baos);
            
            document.open();
            
            // 加载中文字体（使用系统字体或嵌入字体）
            String fontPath = getChineseFontPath();
            com.lowagie.text.Font chineseFont = com.lowagie.text.FontFactory.getFont(
                fontPath, 
                com.lowagie.text.pdf.BaseFont.IDENTITY_H, 
                true, 
                12f
            );
            
            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory.getFont(
                fontPath, 
                com.lowagie.text.pdf.BaseFont.IDENTITY_H, 
                true, 
                16f, 
                com.lowagie.text.Font.BOLD
            );
            
            // 添加标题（居中）
            com.lowagie.text.Paragraph titleParagraph = new com.lowagie.text.Paragraph(title, titleFont);
            titleParagraph.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(20f);
            document.add(titleParagraph);
            
            // 添加正文内容
            String[] lines = textContent.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    // 空行增加间距
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else {
                    com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph(line, chineseFont);
                    paragraph.setSpacingAfter(5f);
                    document.add(paragraph);
                }
            }
            
            document.close();
            
            logger.info("OpenPDF 成功生成 PDF文档");
            
        } catch (Exception e) {
            logger.error("PDF 生成失败：{}", e.getMessage(), e);
            throw new Exception("PDF 生成失败：" + e.getMessage(), e);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 获取中文字体路径，优先使用嵌入字体
     */
    private String getChineseFontPath() throws Exception {
        // 方案 1: 尝试从 classpath 加载嵌入字体（支持 .otf 和 .ttf）
        try {
            // 优先尝试 OTF 格式（更推荐）
            java.io.InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJK-Regular.otf");
            if (fontStream != null) {
                // 复制到临时文件
                java.io.File tempFontFile = java.io.File.createTempFile("embedfont", ".otf");
                tempFontFile.deleteOnExit();
                
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFontFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fontStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                logger.info("使用嵌入字体：Noto Sans CJK (OTF)");
                return tempFontFile.getAbsolutePath();
            }
        } catch (Exception e) {
            logger.debug("无法加载嵌入的 OTF 字体：{}", e.getMessage());
        }
        
        // 尝试 TTF 格式作为备选
        try {
            java.io.InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf");
            if (fontStream != null) {
                java.io.File tempFontFile = java.io.File.createTempFile("embedfont", ".ttf");
                tempFontFile.deleteOnExit();
                
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFontFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fontStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                logger.info("使用嵌入字体：Noto Sans SC (TTF)");
                return tempFontFile.getAbsolutePath();
            }
        } catch (Exception e) {
            logger.debug("无法加载嵌入的 TTF 字体：{}", e.getMessage());
        }
        
        // 方案 2: 使用 Windows 系统字体
        String[] systemFonts = {
            "C:/Windows/Fonts/simhei.ttf",      // 黑体
            "C:/Windows/Fonts/kaiti.ttf",       // 楷体
            "C:/Windows/Fonts/simsun.ttc"       // 宋体
        };
        
        for (String fontPath : systemFonts) {
            java.io.File fontFile = new java.io.File(fontPath);
            if (fontFile.exists()) {
                logger.info("使用系统字体：{}", fontPath);
                return fontPath;
            }
        }
        
        // 如果都没有，抛出异常
        throw new Exception("未找到可用的中文字体，请安装中文字体到 C:/Windows/Fonts/ 或在项目中嵌入 NotoSansCJK-Regular.otf / NotoSansSC-Regular.ttf");
    }
    
    /**
     * 保存转换后的文件到 result 区
     */
    private String saveConvertedFileToResult(byte[] content, String originalName, String targetFormat, String userId) throws Exception {
        // 生成新文件名
        String newFileName = System.currentTimeMillis() + "_" + getFileNameWithExtension(originalName, targetFormat);
        String contentType = getContentTypeForFormat(targetFormat);
        
        // 保存到 GridFS
        ObjectId objectId = gridFsTemplate.store(
            new ByteArrayInputStream(content),
            newFileName,
            contentType
        );
        
        // 创建文件实体
        File resultFile = new File();
        resultFile.setId(objectId.toString());
        resultFile.setFileName(newFileName);
        resultFile.setOriginalName(getFileNameWithExtension(originalName, targetFormat));
        resultFile.setContentType(contentType);
        resultFile.setSize(content.length);
        resultFile.setSection("result");
        resultFile.setUserId(userId);
        resultFile.setUploadTime(LocalDateTime.now());
        resultFile.setFilePath("/api/v1/files/" + objectId.toString() + "/download");
        
        fileRepository.save(resultFile);
        
        logger.info("转换后的文件已保存到 result 区：{}", newFileName);
        
        return objectId.toString();
    }
    
    /**
     * 根据目标格式生成文件名
     */
    private String getFileNameWithExtension(String originalName, String targetFormat) {
        String nameWithoutExt = originalName;
        int lastDotIndex = originalName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExt = originalName.substring(0, lastDotIndex);
        }
        
        return nameWithoutExt + "." + targetFormat;
    }
    
    /**
     * 根据格式获取 Content-Type
     */
    private String getContentTypeForFormat(String format) {
        switch (format) {
            case "pdf":
                return "application/pdf";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":
                return "application/msword";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            case "txt":
                return "text/plain";
            case "md":
                return "text/markdown";
            default:
                return "application/octet-stream";
        }
    }
}
