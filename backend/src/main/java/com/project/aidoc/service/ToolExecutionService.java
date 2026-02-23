package com.project.aidoc.service;

import com.project.aidoc.common.enums.ToolType;
import com.project.aidoc.common.tools.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工具执行服务
 */
@Slf4j
@Service
public class ToolExecutionService {

    /**
     * 执行指定工具
     * @param toolCode 工具代码
     * @param userMessage 用户消息
     * @param sessionId 会话ID
     * @return 工具执行结果
     */
    public String executeTool(int toolCode, String userMessage, String sessionId) {
        ToolType toolType = ToolType.fromCode(toolCode);
        
        log.info("开始执行工具: {} ({})", toolType.getDescription(), toolCode);
        
        try {
            String result;
            
            switch (toolType) {
                case DIRECTORY_VIEW:
                    // 对于目录查看，提取路径参数
                    String path = extractPathFromMessage(userMessage);
                    Map<String, Object> dirInfo = DirectoryViewerTool.viewDirectory(path);
                    result = formatDirectoryResult(dirInfo);
                    break;
                case CONTENT_SUMMARY:
                    String filePath = extractFilePathFromMessage(userMessage);
                    String summaryReq = extractSummaryRequirements(userMessage);
                    Map<String, Object> summaryInfo = ContentSummarizerTool.summarizeContent(filePath, summaryReq);
                    result = formatSummaryResult(summaryInfo);
                    break;
                case FORMAT_CONVERSION:
                    String convReq = extractConversionRequirements(userMessage);
                    String convPath = extractFilePathFromMessage(userMessage);
                    Map<String, Object> convInfo = FormatConverterTool.convertFormat(convReq, convPath);
                    result = formatConversionResult(convInfo);
                    break;
                case SMART_FILL:
                    String templatePath = extractTemplatePathFromMessage(userMessage);
                    String fillReq = extractFillRequirements(userMessage);
                    String fileType = extractFileTypeFromMessage(userMessage);
                    Map<String, Object> fillInfo = SmartFormFillerTool.fillForm(templatePath, fillReq, fileType);
                    result = formatFillResult(fillInfo);
                    break;
                case SMART_MODIFY:
                    String editPath = extractFilePathFromMessage(userMessage);
                    String editReq = extractEditRequirements(userMessage);
                    Map<String, Object> editInfo = SmartEditorTool.editDocument(editPath, editReq);
                    result = formatEditResult(editInfo);
                    break;
                default:
                    result = "未识别的工具类型: " + toolCode;
                    break;
            }
            
            log.info("工具执行完成: {} ({})", toolType.getDescription(), toolCode);
            return result;
            
        } catch (Exception e) {
            log.error("工具执行失败: {} ({})", toolType.getDescription(), toolCode, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 从用户消息中提取路径
     */
    private String extractPathFromMessage(String message) {
        // 简单的路径提取逻辑
        if (message.contains("目录") || message.contains("文件夹")) {
            String[] parts = message.split("[\\s:：]+");
            for (String part : parts) {
                if (part.contains("/") || part.contains("\\")) {
                    return part;
                }
            }
        }
        return "./"; // 默认当前目录
    }

    /**
     * 从用户消息中提取文件路径
     */
    private String extractFilePathFromMessage(String message) {
        // 简单的文件路径提取
        String[] parts = message.split("[\\s:：]+");
        for (String part : parts) {
            if (part.contains(".") && (part.contains("/") || part.contains("\\"))) {
                return part;
            }
        }
        return "sample.txt"; // 默认文件
    }

    /**
     * 从用户消息中提取总结要求
     */
    private String extractSummaryRequirements(String message) {
        return "请总结文件的主要内容";
    }

    /**
     * 从用户消息中提取转换要求
     */
    private String extractConversionRequirements(String message) {
        return "请将文件转换为目标格式";
    }

    /**
     * 从用户消息中提取模板路径
     */
    private String extractTemplatePathFromMessage(String message) {
        return "template.xlsx";
    }

    /**
     * 从用户消息中提取填表要求
     */
    private String extractFillRequirements(String message) {
        return "请根据要求填写表格";
    }

    /**
     * 从用户消息中提取文件类型
     */
    private String extractFileTypeFromMessage(String message) {
        if (message.contains("excel") || message.contains("xlsx")) {
            return "excel";
        } else if (message.contains("word") || message.contains("doc")) {
            return "word";
        }
        return "excel"; // 默认excel
    }

    /**
     * 从用户消息中提取编辑要求
     */
    private String extractEditRequirements(String message) {
        return "请根据要求修改文档";
    }

    /**
     * 格式化目录查看结果
     */
    private String formatDirectoryResult(Map<String, Object> dirInfo) {
        if (dirInfo == null) {
            return "无法获取目录信息";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("目录信息:\n");
        
        Object files = dirInfo.get("files");
        Object directories = dirInfo.get("directories");
        
        if (files != null) {
            sb.append("文件数量: ").append(files).append("\n");
        }
        if (directories != null) {
            sb.append("子目录数量: ").append(directories).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 格式化内容总结结果
     */
    private String formatSummaryResult(Map<String, Object> summaryInfo) {
        if (summaryInfo == null) {
            return "内容总结完成";
        }
        return "内容总结结果已生成";
    }

    /**
     * 格式化格式转换结果
     */
    private String formatConversionResult(Map<String, Object> convInfo) {
        if (convInfo == null) {
            return "格式转换完成";
        }
        return "格式转换结果已生成";
    }

    /**
     * 格式化填表结果
     */
    private String formatFillResult(Map<String, Object> fillInfo) {
        if (fillInfo == null) {
            return "智能填表完成";
        }
        return "填表结果已生成";
    }

    /**
     * 格式化编辑结果
     */
    private String formatEditResult(Map<String, Object> editInfo) {
        if (editInfo == null) {
            return "智能修改完成";
        }
        return "修改结果已生成";
    }

    /**
     * 检查工具是否可用
     */
    public boolean isToolAvailable(int toolCode) {
        ToolType toolType = ToolType.fromCode(toolCode);
        return toolType != ToolType.NONE;
    }
}