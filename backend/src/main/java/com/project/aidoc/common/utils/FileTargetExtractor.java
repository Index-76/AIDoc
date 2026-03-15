package com.project.aidoc.common.utils;

import com.project.aidoc.entity.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件目标提取工具
 * 用于从用户消息中提取目标文件或区域信息
 * 
 * <h2>功能说明：</h2>
 * <ul>
 * <li><strong>区域识别</strong>：支持识别 wait、read、template、result 四个区域</li>
 * <li><strong>文件识别</strong>：支持从消息中提取文件名或路径</li>
 * <li><strong>文件匹配</strong>：三级匹配策略（精确匹配 > 无扩展名匹配 > 模糊子串匹配）</li>
 * </ul>
 * 
 * <h2>使用场景：</h2>
 * <ol>
 * <li>无特殊要求时默认处理读取区（read）所有文件</li>
 * <li>用户指定某个区域时，处理该区域所有文件</li>
 * <li>用户指定某个文件时，只处理该文件</li>
 * </ol>
 * 
 * <h2>示例：</h2>
 * 
 * <pre>
 * {@code
 * // 在 Service 中注入并使用
 * @Autowired
 * private FileTargetExtractor fileTargetExtractor;
 * 
 * // 1. 提取区域
 * String section = fileTargetExtractor.extractSectionFromMessage("总结读取区的文件");
 * // 返回："read"
 * 
 * // 2. 提取文件名
 * String fileName = fileTargetExtractor.extractFilePathFromMessage("将 test.pdf 转换为 word");
 * // 返回："test.pdf"
 * 
 * // 3. 匹配文件
 * File matchedFile = fileTargetExtractor.findMatchingFile(files, "test");
 * // 返回：匹配的文件对象
 * }
 * </pre>
 */

@Slf4j
@Component
public class FileTargetExtractor {

    /**
     * 从消息中提取目标区域（wait/read/template/result）
     *
     * @param message 用户消息
     * @return 目标区域标识，未指定时返回 null
     */
    public String extractSectionFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // 支持多种表达方式（不区分大小写）
        String lowerMessage = message.toLowerCase();

        // === 读取区（read）===
        if (lowerMessage.contains("读取") || lowerMessage.contains("read")) {
            return "read";
        }

        // === 等待区（wait）===
        if (lowerMessage.contains("等待") || lowerMessage.contains("wait")) {
            return "wait";
        }

        // === 模板区（template）===
        if (lowerMessage.contains("模板") || lowerMessage.contains("template")) {
            return "template";
        }

        // === 结果区（result）===
        if (lowerMessage.contains("结果") || lowerMessage.contains("result") ||
                lowerMessage.contains("输出") || lowerMessage.contains("导出")) {
            return "result";
        }

        return null; // 没有明确指定区域
    }

    /**
     * 从消息中提取文件路径或文件名
     *
     * @param message 用户消息
     * @return 文件路径或文件名，未找到时返回 null
     */
    public String extractFilePathFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        log.debug("尝试从消息中提取文件路径或文件名：{}", message);

        // === 策略 1：尝试提取完整的文件路径（包含 / 或 \）===
        String[] parts = message.split("[\\s:：,，]+");
        for (String part : parts) {
            if (part.contains(".") && (part.contains("/") || part.contains("\\"))) {
                log.info("提取到完整文件路径：{}", part);
                return part;
            }
        }

        // === 策略 2：使用正则表达式匹配带扩展名的文件名（优先匹配）===
        // 扩展常见文档扩展名列表，支持更多文件格式
        String filePattern = "([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)\\[\\]\\.]+\\.(pdf|docx|doc|xlsx|xls|txt|md|markdown|csv|pptx|ppt|rtf|wps|et|dps|epub|mobi|azw3|jpg|jpeg|png|gif|bmp|tiff|zip|rar|7z|tar|gz))";
        Pattern pattern = Pattern.compile(filePattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String fileName = matcher.group(1).trim();
            // 清理首尾的标点符号和空格
            fileName = fileName.replaceAll("^[,，.。:\\s]+", "")
                    .replaceAll("[,，.。:\\s]+$", "");
            log.info("通过正则匹配到文件名（带扩展名）：{}", fileName);
            return fileName;
        }

        // === 策略 3：尝试从特定句式中提取带扩展名的文件名 ===
        // 例如："将 xxx.pdf 转换为 word"、"转换 xxx.docx 为 pdf"
        String[] extractPatterns = {
                "将\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)\\[\\]\\.]+\\.[a-zA-Z0-9]+)\\s*(?:转换 | 转 |变为 | 处理 |分析 |总结)",
                "转换\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)\\[\\]\\.]+\\.[a-zA-Z0-9]+)\\s*(?:为 | 成 |到)",
                "把\\s*([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)\\[\\]\\.]+\\.[a-zA-Z0-9]+)\\s*(?:转换 | 转 |变为 | 处理 |分析 |总结)",
                "(?:总结 | 分析 |处理 |查看)\\s*(?:的 | 这个 | 那个 | 一下)?([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)\\[\\]\\.]+\\.[a-zA-Z0-9]+)"
        };

        for (String extractPattern : extractPatterns) {
            Pattern extractPat = Pattern.compile(extractPattern, Pattern.CASE_INSENSITIVE);
            Matcher extractMat = extractPat.matcher(message);

            if (extractMat.find()) {
                String fileName = extractMat.group(1).trim();
                // 清理首尾的标点符号和空格
                fileName = fileName.replaceAll("^[,，.。:\\s]+", "")
                        .replaceAll("[,，.。:\\s]+$", "");
                log.info("通过句式模式匹配到文件名（带扩展名）：{}", fileName);
                return fileName;
            }
        }

        // === 策略 4：提取不带扩展名的文件名（针对"自动转换/总结 XXX"的句式）===
        // 例如："自动转换默认模块"、"总结报告"、"转换测试文件"
        String[] keywordPatterns = {
                "(?:自动 | 帮我 | 请)?(?:转换 | 总结 |分析 |处理)\\s*(?:的 | 这个 | 这个 |那个)?([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]{2,20})(?:的 | 格式 | 内容 | 一下)?\\s*$",
                "(?:将 | 把 | 对)\\s*(?:的 | 这个 | 这个 |那个)?([\\u4e00-\\u9fa5\\w\\-\\s()\\(\\)]{2,20})(?:进行 | 做)?\\s*(?:转换 | 总结 |分析 |处理)"
        };

        for (String keywordPattern : keywordPatterns) {
            Pattern keywordPat = Pattern.compile(keywordPattern, Pattern.CASE_INSENSITIVE);
            Matcher keywordMat = keywordPat.matcher(message);

            if (keywordMat.find()) {
                String fileName = keywordMat.group(1).trim();
                // 清理首尾的标点符号和空格，并过滤掉常见停用词
                fileName = fileName.replaceAll("^[,，.。:\\s]+", "")
                        .replaceAll("[,，.。:\\s]+$", "");
                
                // 确保不是停用词或无意义的词
                if (fileName.length() >= 2 && 
                    !fileName.equals("什么") && 
                    !fileName.equals("这个") &&
                    !fileName.equals("那个") &&
                    !fileName.equals("文件")) {
                    log.info("通过关键词模式匹配到文件名（不带扩展名）：{}", fileName);
                    return fileName;
                }
            }
        }

        log.debug("未从消息中提取到有效的文件路径或文件名");
        return null; // 未找到文件
    }

    /**
     * 在文件列表中匹配目标文件
     *
     * @param files      候选文件列表
     * @param searchName 搜索名称（文件名或路径）
     * @return 匹配的文件对象，未找到返回 null
     */
    public File findMatchingFile(List<File> files, String searchName) {
        if (searchName == null || files == null || files.isEmpty()) {
            return null;
        }

        log.debug("开始匹配文件，搜索词：{}, 候选文件数：{}", searchName, files.size());

        // === 第一级：精确匹配文件名（含扩展名）===
        // 优先匹配完全一致的文件名（包括扩展名）
        for (File file : files) {
            if (file.getOriginalName().equals(searchName) ||
                    file.getFileName().equals(searchName)) {
                log.info("精确匹配到文件：{} (original: {}, fileName: {})",
                        searchName, file.getOriginalName(), file.getFileName());
                return file;
            }
        }

        // === 第二级：带扩展名的智能匹配 ===
        // 如果搜索词包含扩展名，优先匹配相同扩展名的文件
        String searchNameLower = searchName.toLowerCase();
        if (searchNameLower.contains(".")) {
            String searchExt = searchNameLower.substring(searchNameLower.lastIndexOf("."));
            log.debug("搜索词包含扩展名：{}, 尝试扩展名匹配：{}", searchName, searchExt);
            
            for (File file : files) {
                String originalNameLower = file.getOriginalName().toLowerCase();
                String fileNameLower = file.getFileName().toLowerCase();
                
                // 检查扩展名是否匹配
                if (originalNameLower.endsWith(searchExt) || fileNameLower.endsWith(searchExt)) {
                    String originalWithoutExt = removeFileExtension(file.getOriginalName());
                    String searchWithoutExt = removeFileExtension(searchName);
                    
                    // 去除扩展名后进行精确匹配
                    if (originalWithoutExt.equals(searchWithoutExt) ||
                            removeFileExtension(file.getFileName()).equals(searchWithoutExt)) {
                        log.info("带扩展名匹配到文件：{} -> {}", searchName, file.getOriginalName());
                        return file;
                    }
                }
            }
        }

        // === 第三级：无扩展名匹配 ===
        // 去除扩展名后进行匹配（适用于不带扩展名的搜索）
        String searchNameWithoutExt = removeFileExtension(searchName);
        log.debug("精确匹配失败，尝试无扩展名匹配：{}", searchNameWithoutExt);

        for (File file : files) {
            String originalNameWithoutExt = removeFileExtension(file.getOriginalName());
            String fileNameWithoutExt = removeFileExtension(file.getFileName());

            if (originalNameWithoutExt.equals(searchNameWithoutExt) ||
                    fileNameWithoutExt.equals(searchNameWithoutExt)) {
                log.info("无扩展名匹配到文件：{} -> {}", searchName, file.getOriginalName());
                return file;
            }
        }

        // === 第四级：宽松模糊匹配（仅当搜索词是完整子串时）===
        // 作为最后的匹配手段，避免误匹配
        log.debug("无扩展名匹配失败，尝试宽松模糊匹配");

        for (File file : files) {
            // 忽略大小写进行子串匹配
            if (file.getOriginalName().toLowerCase().contains(searchName.toLowerCase()) ||
                    file.getFileName().toLowerCase().contains(searchName.toLowerCase())) {
                log.warn("宽松模糊匹配到文件：{} -> {} (可能存在误匹配，请用户确认)",
                        searchName, file.getOriginalName());
                return file;
            }
        }

        log.info("未匹配到文件：{}", searchName);
        return null;
    }

    /**
     * 移除文件扩展名
     *
     * @param fileName 文件名
     * @return 不带扩展名的文件名
     */
    public String removeFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    /**
     * 获取区域显示名称
     *
     * @param section 区域标识
     * @return 中文显示名称
     */
    public String getSectionDisplayName(String section) {
        if (section == null) {
            return "未知区域";
        }
        switch (section) {
            case "wait":
                return "等待区";
            case "read":
                return "读取区";
            case "template":
                return "模板区";
            case "result":
                return "结果区";
            default:
                return "未知区域 (" + section + ")";
        }
    }

    /**
     * 过滤掉缓存文件（temp 区的总结文件）
     *
     * @param files 文件列表
     * @return 过滤后的真实文件列表
     */
    public List<File> filterOutCacheFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        return files.stream()
                .filter(file -> {
                    String fileName = file.getOriginalName();
                    // 过滤掉 temp 区的缓存文件（文件名包含_summary.json 的）
                    return !(fileName != null && fileName.contains("_summary.json"));
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
