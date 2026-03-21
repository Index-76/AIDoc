package com.project.aidoc.common.tools;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import com.project.aidoc.common.utils.ExcelToTemplate;
import com.project.aidoc.common.utils.MultiFileToMultiTemplateProcessor; // 替换 TxtToTemplate
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
 * 
 * 决策逻辑：
 * - 若读取区只有一个文件且为 Excel，且模板区也只有一个文件 → 使用 ExcelToTemplate（可针对单 Excel 数据源进行 AI 筛选）
 * - 其他情况（多文件、非 Excel 读取文件等）→ 使用 MultiFileToMultiTemplateProcessor（支持混合文件源、多模板）
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
    public String executeFillForm(String userId, String userMessage) {
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

            // 记录文件信息
            log.info("读取区文件数量：{}", readFiles.size());
            log.info("模板区文件数量：{}", templateFiles.size());

            // 判断是否满足单 Excel 读取 + 单模板的专用路径
            boolean useExcelToTemplate = false;
            if (readFiles.size() == 1 && isExcelFile(readFiles.get(0))) {
                if (templateFiles.size() == 1) {
                    useExcelToTemplate = true;
                } else {
                    log.info("读取区为单 Excel 文件，但模板区有多个文件，将使用多模板处理器");
                }
            } else {
                log.info("读取区包含多个文件或非 Excel 文件，将使用多模板处理器");
            }

            String result;
            if (useExcelToTemplate) {
                // 单 Excel 读取 + 单模板
                File readFile = readFiles.get(0);
                File templateFile = templateFiles.get(0);
                boolean isTemplateWord = !isExcelFile(templateFile); // 模板非 Excel 即为 Word

                log.info("调用 ExcelToTemplate 处理 (读取: {}, 模板: {})",
                        readFile.getOriginalName(), templateFile.getOriginalName());
                result = ExcelToTemplate.processWithAI(
                        readFile, templateFile, userId, fileService,
                        userConfigService, apiService, userMessage, isTemplateWord);
            } else {
                // 其他情况：使用多模板处理器
                log.info("调用 MultiFileToMultiTemplateProcessor 处理，读取文件 {} 个，模板文件 {} 个",
                        readFiles.size(), templateFiles.size());
                result = MultiFileToMultiTemplateProcessor.process(
                        readFiles, templateFiles, userId, fileService,
                        userConfigService, apiService, userMessage);
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