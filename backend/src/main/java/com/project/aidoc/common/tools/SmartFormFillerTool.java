package com.project.aidoc.common.tools;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import com.project.aidoc.common.utils.ExcelToExcel;
import com.project.aidoc.common.utils.TxtToExcel;
import com.project.aidoc.common.utils.TxtToWord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能填表工具类
 * 支持四种文件类型组合的智能填表功能：
 * - Excel to Excel
 * - Excel to Word
 * - Word to Excel
 * - Word to Word
 */
@Slf4j
@Component
public class SmartFormFillerTool {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserConfigService userConfigService;
    
    @Autowired
    private ApiService apiService;

    /**
     * 执行智能填表功能
     * 
     * @param userId      用户 ID
     * @param userMessage 用户消息
     * @return 执行结果
     */
    public String executeFillForm(Long userId, String userMessage) {
        log.info("开始执行智能填表功能，用户 ID: {}", userId);

        try {
            // 检查 read 和 template 区域的文件
            List<File> readFiles = fileService.getFilesByUserIdAndSection(userId, "read");
            List<File> templateFiles = fileService.getFilesByUserIdAndSection(userId, "template");

            // 检查是否有文件
            if (readFiles.isEmpty() || templateFiles.isEmpty()) {
                String errorMsg = "智能填表失败：";
                if (readFiles.isEmpty() && templateFiles.isEmpty()) {
                    errorMsg += "读取区和模板区均无文件";
                } else if (readFiles.isEmpty()) {
                    errorMsg += "读取区无文件";
                } else {
                    errorMsg += "模板区无文件";
                }
                log.warn(errorMsg);
                return errorMsg;
            }

            // 获取第一个文件（假设每个区域只有一个文件用于填表）
            File readFile = readFiles.get(0);
            File templateFile = templateFiles.get(0);

            log.info("找到读取文件：{} (类型：{})", readFile.getOriginalName(), readFile.getContentType());
            log.info("找到模板文件：{} (类型：{})", templateFile.getOriginalName(), templateFile.getContentType());

            // 判断文件类型
            boolean isReadExcel = isExcelFile(readFile);
            boolean isTemplateExcel = isExcelFile(templateFile);

            String result;
            if (isReadExcel && isTemplateExcel) {
                // Excel to Excel
                log.info("调用 ExcelToExcel 处理");
                result = ExcelToExcel.process(readFile, templateFile, userId, fileService);
            } else if (isReadExcel && !isTemplateExcel) {
                // Excel to Word
                log.info("调用 ExcelToWord 处理（暂未实现）");
                result = "暂不支持 Excel 到 Word 的填表功能";
            } else if (!isReadExcel && isTemplateExcel) {
                // Word to Excel
                log.info("调用 TxtToExcel 处理");
                result = TxtToExcel.process(readFile, templateFile, userId, fileService, userConfigService);
            } else {
                // Word to Word
                log.info("调用 TxtToWord 处理");
                result = TxtToWord.process(readFile, templateFile, userId, fileService, userConfigService, apiService);
            }

            log.info("智能填表执行完成：{}", result);
            return result;

        } catch (Exception e) {
            log.error("智能填表执行失败", e);
            return "智能填表执行失败：" + e.getMessage();
        }
    }

    /**
     * 判断文件是否为 Excel 格式
     * 
     * @param file 文件对象
     * @return 是否为 Excel 文件
     */
    private boolean isExcelFile(File file) {
        if (file == null || file.getContentType() == null) {
            return false;
        }

        String contentType = file.getContentType().toLowerCase();
        String fileName = file.getOriginalName().toLowerCase();

        // 检查 MIME 类型
        if (contentType.contains("excel") ||
                contentType.contains("spreadsheet") ||
                contentType.equals("application/vnd.ms-excel") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return true;
        }

        // 检查文件扩展名
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return true;
        }

        return false;
    }
}