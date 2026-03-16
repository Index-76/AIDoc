package com.project.aidoc.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.File;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Excel 到模板填表工具类
 * 支持 Excel → Excel 和 Excel → Word 两种模式
 */
@Slf4j
public class ExcelToTemplate {

    private static final String MISSING_VALUE_MARKER = "<null>";
    private static int MAX_CONCURRENT_AI_CALLS = Integer
            .parseInt(System.getProperty("excel.template.max.concurrent", "5"));
    private static long AI_EXTRACT_TIMEOUT_MINUTES = Long
            .parseLong(System.getProperty("ai.extract.timeout.minutes", "5"));

    /**
     * Excel填表处理主方法（简化版，无AI筛选）
     * 
     * @param readFile     读取区Excel文件
     * @param templateFile 模板文件
     * @param userId       用户 ID
     * @param fileService  文件服务
     * @return 处理结果
     */
    public static String process(File readFile, File templateFile, String userId, FileService fileService) {
        try {
            log.info("========== 开始执行 Excel 到模板填表处理 ==========");
            log.info("读取文件：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
            log.info("模板文件：{} (ID: {})", templateFile.getOriginalName(), templateFile.getId());

            // 步骤 1: 获取读取区Excel数据，转换成 JSON
            log.info("【步骤 1】读取 Excel数据并转换为 JSON...");
            byte[] readContent = fileService.getFileContent(readFile.getId(), userId);
            if (readContent == null) {
                return "❌ 获取读取文件内容失败";
            }

            List<Map<String, Object>> excelData = extractExcelDataToJSON(readContent);
            log.info("成功提取 {} 行数据", excelData.size());

            // 步骤 2: 根据模板文件，调用 HeaderExtractorUtil 获取表头
            log.info("【步骤 2】提取模板表头信息...");
            Map<String, Object> headerInfo = HeaderExtractorUtil.extractTemplateHeaders(templateFile, userId,
                    fileService);
            if (headerInfo == null) {
                return "❌ 提取模板表头信息失败";
            }

            List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");
            log.info("模板包含 {} 个工作表", sheets.size());

            // 步骤 3-4: 遍历数据，根据表头进行匹配
            log.info("【步骤 3-4】准备填充数据...");
            Map<String, List<Object[]>> mergedData = prepareDataForSheets(excelData, sheets);

            // 步骤 5: 调用 JsonToExcelWriterUtil 写入 Excel
            log.info("【步骤 5】填充 Excel 模板...");
            String resultFileId = JsonToExcelWriterUtil.fillExcelTemplate(
                    templateFile.getId(), mergedData, headerInfo, userId, fileService);

            log.info("========== Excel填表处理完成 ==========");
            return "✅ 已完成 Excel 智能填表操作，共处理 " + excelData.size() + " 行数据";

        } catch (Exception e) {
            log.error("Excel 到模板填表处理失败", e);
            return "❌ Excel填表失败：" + e.getMessage();
        }
    }

    /**
     * Excel填表处理完整方法（支持 AI 筛选和 Word/Excel 模板）
     * 
     * @param readFile          读取区Excel文件
     * @param templateFile      模板文件
     * @param userId            用户 ID
     * @param fileService       文件服务
     * @param userConfigService 用户配置服务
     * @param apiService        API 服务
     * @param userMessage       用户消息（包含筛选条件）
     * @param isWordTemplate    是否为 Word 模板
     * @return 处理结果
     */
    public static String processWithAI(File readFile, File templateFile, String userId,
            FileService fileService, UserConfigService userConfigService,
            ApiService apiService, String userMessage, boolean isWordTemplate) {
        Set<String> tempFileIds = new HashSet<>();
        boolean success = false;

        try {
            log.info("========== 开始执行 Excel 到{}模板的智能填表 ==========", isWordTemplate ? "Word" : "Excel");
            log.info("读取文件：{} (ID: {})", readFile.getOriginalName(), readFile.getId());
            log.info("模板文件：{} (ID: {})", templateFile.getOriginalName(), templateFile.getId());
            log.info("用户消息：{}", userMessage);

            // 步骤 1: 提取读取区Excel数据
            log.info("【步骤 1】读取 Excel数据并转换为 JSON...");
            byte[] readContent = fileService.getFileContent(readFile.getId(), userId);
            if (readContent == null) {
                return "❌ 获取读取文件内容失败";
            }

            List<Map<String, Object>> excelData = extractExcelDataToJSON(readContent);
            log.info("成功提取 {} 行数据", excelData.size());

            // 将读取数据保存为临时 JSON 文件供 AI 使用
            String dataFileId = saveExcelDataAsJSON(excelData, readFile.getId(), userId, fileService);
            tempFileIds.add(dataFileId);

            // 步骤 2: 提取模板表头信息
            log.info("【步骤 2】提取模板{}信息...", isWordTemplate ? "表格" : "工作表");
            Map<String, Object> headerInfo;
            if (isWordTemplate) {
                headerInfo = WordTableExtractorUtil.extractTables(templateFile, userId, fileService);
            } else {
                headerInfo = HeaderExtractorUtil.extractTemplateHeaders(templateFile, userId, fileService);
            }

            if (headerInfo == null) {
                return "❌ 提取模板表头信息失败";
            }

            String headerFileId = (String) headerInfo.get("fileId");
            tempFileIds.add(headerFileId);

            List<Map<String, Object>> tablesOrSheets = isWordTemplate
                    ? (List<Map<String, Object>>) headerInfo.get("tables")
                    : (List<Map<String, Object>>) headerInfo.get("sheets");
            log.info("模板包含 {} 个{}}", tablesOrSheets.size(), isWordTemplate ? "表格" : "工作表");

            // 步骤 3: AI 解析用户消息，提取筛选参数
            log.info("【步骤 3】AI 解析用户消息，提取筛选参数...");
            FilterCriteria filterCriteria = parseFilterCriteriaFromUserMessage(
                    userMessage, excelData, tablesOrSheets.get(0), userId, userConfigService, apiService);
            log.info("AI 解析的筛选条件：{}", filterCriteria);

            // 步骤 4: 根据筛选条件过滤数据
            log.info("【步骤 4】根据筛选条件过滤数据...");
            List<Map<String, Object>> filteredData = applyFilterCriteria(excelData, filterCriteria);
            log.info("过滤后剩余 {} 行数据", filteredData.size());

            // 步骤 5: 为每个表格/工作表准备数据
            log.info("【步骤 5】准备填充数据...");
            Map<Integer, List<Object[]>> tableDataMap = prepareDataForTables(filteredData, tablesOrSheets);

            // 步骤 6: 创建模板副本
            log.info("【步骤 6】创建模板副本...");
            String filledFileId = createTemplateCopy(templateFile, userId, fileService, isWordTemplate);
            tempFileIds.add(filledFileId);

            // 步骤 7: 填充模板
            log.info("【步骤 7】填充{}模板...", isWordTemplate ? "Word" : "Excel");
            String resultFileId;
            if (isWordTemplate) {
                resultFileId = WordSplicerUtil.fillWordTemplate(filledFileId, tableDataMap,
                        headerInfo, userId, fileService, templateFile.getOriginalName());
            } else {
                Map<String, List<Object[]>> sheetDataMap = new HashMap<>();
                List<Map<String, Object>> sheets = (List<Map<String, Object>>) headerInfo.get("sheets");

                for (int i = 0; i < sheets.size(); i++) {
                    Map<String, Object> sheetInfo = sheets.get(i);
                    String sheetName = (String) sheetInfo.get("sheetName");
                    List<Object[]> rowData = tableDataMap.get(i);

                    if (rowData != null && !rowData.isEmpty()) {
                        sheetDataMap.put(sheetName, rowData);
                        log.info("将 {} 行数据映射到工作表 '{}'", rowData.size(), sheetName);
                    } else {
                        log.warn("表格索引 {} 没有对应的数据，跳过工作表 '{}'", i, sheetName);
                    }
                }

                resultFileId = JsonToExcelWriterUtil.fillExcelTemplate(filledFileId,
                        sheetDataMap, headerInfo, userId, fileService);
            }

            tempFileIds.remove(filledFileId);
            tempFileIds.add(resultFileId);

            // 步骤 8: 清理临时文件并移动结果
            log.info("【步骤 8】清理临时文件并移动结果...");
            cleanupAndMoveResult(resultFileId, tempFileIds, userId, fileService);

            log.info("========== Excel 到{}模板填表处理完成 ==========", isWordTemplate ? "Word" : "Excel");
            return String.format("✅ 已完成智能填表操作，从 %d 行数据中筛选出 %d 行并填充到模板",
                    excelData.size(), filteredData.size());

        } catch (Exception e) {
            log.error("Excel 到模板填表处理失败", e);
            if (!success) {
                log.info("检测到处理失败，开始清理临时文件（共 {} 个）", tempFileIds.size());
                for (String fileId : tempFileIds) {
                    try {
                        fileService.deleteFileById(fileId, userId);
                    } catch (Exception ex) {
                        log.warn("删除临时文件失败：{}", fileId, ex);
                    }
                }
            }
            return "❌ 填表失败：" + e.getMessage();
        }
    }

    /**
     * 从 Excel 字节数组中提取数据并转换为 JSON格式
     */
    private static List<Map<String, Object>> extractExcelDataToJSON(byte[] excelContent) throws Exception {
        List<Map<String, Object>> allData = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelContent))) {
            int sheetNum = workbook.getNumberOfSheets();

            for (int i = 0; i < sheetNum; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // 获取表头（第一行）
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    log.warn("工作表 {} 没有表头，跳过", sheet.getSheetName());
                    continue;
                }

                // 提取表头列名
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }

                // 提取数据行
                int lastRowNum = sheet.getLastRowNum();
                for (int r = 1; r <= lastRowNum; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) {
                        continue;
                    }

                    Map<String, Object> rowData = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.getCell(c);
                        String value = cell != null ? getCellValueAsString(cell) : "";
                        rowData.put(headers.get(c), value);
                    }
                    allData.add(rowData);
                }
            }
        }

        return allData;
    }

    /**
     * 为每个工作表准备数据（简化版本）
     */
    private static Map<String, List<Object[]>> prepareDataForSheets(
            List<Map<String, Object>> excelData,
            List<Map<String, Object>> sheets) {

        Map<String, List<Object[]>> mergedData = new HashMap<>();

        // 简单策略：将所有数据分配到第一个工作表
        if (!sheets.isEmpty()) {
            Map<String, Object> firstSheet = sheets.get(0);
            String sheetName = (String) firstSheet.get("sheetName");
            List<String> targetHeaders = (List<String>) firstSheet.get("headers");

            List<Object[]> rows = new ArrayList<>();
            for (Map<String, Object> rowData : excelData) {
                Object[] rowArray = new Object[targetHeaders.size()];
                for (int i = 0; i < targetHeaders.size(); i++) {
                    String header = targetHeaders.get(i);
                    // 忽略大小写匹配列名
                    Object value = findValueByHeader(rowData, header);
                    rowArray[i] = value != null ? value : MISSING_VALUE_MARKER;
                }
                rows.add(rowArray);
            }

            mergedData.put(sheetName, rows);
            log.info("工作表 '{}' 准备 {} 行数据", sheetName, rows.size());
        }

        return mergedData;
    }

    /**
     * 为每个表格/工作表准备数据
     * 
     * @param excelData      Excel 数据
     * @param tablesOrSheets 表格/工作表信息列表
     * @return 表格索引到行数据的映射
     */
    private static Map<Integer, List<Object[]>> prepareDataForTables(
            List<Map<String, Object>> excelData,
            List<Map<String, Object>> tablesOrSheets) {

        Map<Integer, List<Object[]>> tableDataMap = new HashMap<>();

        // 遍历所有表格/工作表
        for (int i = 0; i < tablesOrSheets.size(); i++) {
            Map<String, Object> tableInfo = tablesOrSheets.get(i);
            List<String> targetHeaders = (List<String>) tableInfo.get("headers");

            List<Object[]> rows = new ArrayList<>();
            for (Map<String, Object> rowData : excelData) {
                Object[] rowArray = new Object[targetHeaders.size()];
                for (int j = 0; j < targetHeaders.size(); j++) {
                    String header = targetHeaders.get(j);
                    Object value = findValueByHeader(rowData, header);
                    rowArray[j] = value != null ? value : MISSING_VALUE_MARKER;
                }
                rows.add(rowArray);
            }

            tableDataMap.put(i, rows);
            log.info("表格/工作表 {} ({}) 准备 {} 行数据", i, tableInfo.get("sheetName"), rows.size());
        }

        return tableDataMap;
    }

    /**
     * 根据表头名称查找对应的值（忽略大小写）
     */
    private static Object findValueByHeader(Map<String, Object> rowData, String header) {
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(header.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 判断行是否为空
     */
    private static boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK &&
                    !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取单元格值的字符串表示
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double num = cell.getNumericCellValue();
                    return (num == (long) num) ? String.valueOf((long) num) : String.valueOf(num);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    /**
     * 保存 Excel数据为临时 JSON 文件
     */
    private static String saveExcelDataAsJSON(List<Map<String, Object>> excelData, String readFileId,
            String userId, FileService fileService) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(excelData);
        String fileName = readFileId + "_data.json";

        MultipartFile jsonFile = JsonToExcelWriterUtil.createMultipartFile(
                fileName, fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8));

        File savedFile = fileService.saveFile(jsonFile, "temp", userId);
        log.info("Excel数据已保存到临时文件：{} (ID: {})", fileName, savedFile.getId());
        return savedFile.getId();
    }

    /**
     * 创建模板副本
     */
    private static String createTemplateCopy(File templateFile, String userId,
            FileService fileService, boolean isWordTemplate) throws Exception {
        byte[] content = fileService.getFileContent(templateFile.getId(), userId);
        if (content == null)
            return null;

        String extension = isWordTemplate ? ".docx" : ".xlsx";
        String contentType = isWordTemplate
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        String fileName = templateFile.getId() + "_filled" + extension;
        MultipartFile copy = JsonToExcelWriterUtil.createMultipartFile(fileName, fileName, contentType, content);

        File savedFile = fileService.saveFile(copy, "temp", userId);
        log.info("模板副本已创建：{} (ID: {})", fileName, savedFile.getId());
        return savedFile.getId();
    }

    /**
     * AI 解析用户消息，提取筛选参数
     */
    private static FilterCriteria parseFilterCriteriaFromUserMessage(
            String userMessage, List<Map<String, Object>> excelData,
            Map<String, Object> tableInfo, String userId,
            UserConfigService userConfigService, ApiService apiService) throws Exception {

        // 获取用户配置
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null) {
            log.warn("未找到用户配置，使用默认筛选条件（不过滤）");
            return new FilterCriteria();
        }

        // 构建 AI 提示词
        String prompt = buildFilterCriteriaPrompt(userMessage, excelData, tableInfo);

        // 调用 AI 服务
        String aiResponse = callAIWithRetry(prompt, userConfig, apiService);

        // 解析 AI 响应
        return parseFilterCriteriaFromAIResponse(aiResponse);
    }

    /**
     * 构建筛选参数提取的 AI 提示词
     */
    private static String buildFilterCriteriaPrompt(String userMessage, List<Map<String, Object>> excelData,
            Map<String, Object> tableInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能填表助手，需要从用户消息中提取数据筛选条件。\n\n");
        prompt.append("【用户消息】\n").append(userMessage).append("\n\n");

        // 提供数据示例
        if (!excelData.isEmpty()) {
            prompt.append("【可用数据列】\n");
            excelData.get(0).keySet().forEach(col -> prompt.append("- ").append(col).append("\n"));
            prompt.append("\n");

            prompt.append("【数据示例（前 3 行）】\n");
            for (int i = 0; i < Math.min(3, excelData.size()); i++) {
                prompt.append("行").append(i + 1).append(": ").append(excelData.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        // 提供表头信息
        List<String> headers = (List<String>) tableInfo.get("headers");
        prompt.append("【目标表格表头】\n");
        headers.forEach(h -> prompt.append("- ").append(h).append("\n"));
        prompt.append("\n");

        // 说明输出格式
        prompt.append("【任务要求】\n");
        prompt.append("请从用户消息中提取筛选条件，并以 JSON 格式返回：\n");
        prompt.append("{\n");
        prompt.append("  \"filters\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"column\": \"列名\",\n");
        prompt.append("      \"mode\": 0/1/2/3/4 (0:单项匹配，1:比较匹配，2:范围匹配，3:多选匹配，4:行号匹配),\n");
        prompt.append("      \"values\": [\"值 1\", \"值 2\", ...]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("直接返回 JSON，不要有任何说明文字。\n");

        return prompt.toString();
    }

    /**
     * 调用 AI 服务（带重试机制）
     */
    private static String callAIWithRetry(String prompt, UserConfig userConfig, ApiService apiService)
            throws Exception {
        int maxRetries = 3;
        long initialDelay = 1000; // 1 秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = apiService.callExternalApi(
                        userConfig.getSiliconFlowBaseUrl(),
                        userConfig.getSiliconFlowApiKey(),
                        prompt,
                        userConfig.getAnalysisModelName());

                if (response != null && !response.trim().isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("AI 调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(initialDelay * attempt); // 递增延迟
                }
            }
        }

        throw new Exception("AI 调用失败，已达到最大重试次数");
    }

    /**
     * 从 AI 响应中解析筛选参数
     */
    private static FilterCriteria parseFilterCriteriaFromAIResponse(String aiResponse) throws Exception {
        FilterCriteria criteria = new FilterCriteria();

        try {
            // 提取 JSON 部分
            String jsonContent = extractJsonFromResponse(aiResponse);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonContent, Map.class);

            List<Map<String, Object>> filters = (List<Map<String, Object>>) jsonMap.getOrDefault("filters",
                    new ArrayList<>());

            for (Map<String, Object> filter : filters) {
                String column = (String) filter.getOrDefault("column", "");
                int mode = ((Number) filter.getOrDefault("mode", 0)).intValue();
                List<String> values = (List<String>) filter.getOrDefault("values", new ArrayList<>());

                criteria.addFilter(column, mode, values);
            }
        } catch (Exception e) {
            log.warn("解析 AI 响应失败，使用默认筛选条件（不过滤）: {}", e.getMessage());
        }

        return criteria;
    }

    /**
     * 从响应中提取 JSON 内容
     */
    private static String extractJsonFromResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.trim();
    }

    /**
     * 应用筛选条件到数据
     */
    private static List<Map<String, Object>> applyFilterCriteria(
            List<Map<String, Object>> excelData, FilterCriteria criteria) {

        if (criteria.getFilters().isEmpty()) {
            return excelData; // 无筛选条件，返回全部数据
        }

        // 检查是否有行号匹配（mode=4）
        FilterCondition rowNumberFilter = null;
        for (FilterCondition condition : criteria.getFilters()) {
            if (condition.getMode() == 4) {
                rowNumberFilter = condition;
                break;
            }
        }

        // 如果有行号匹配，直接按行号提取数据
        if (rowNumberFilter != null) {
            return extractRowsByNumbers(excelData, rowNumberFilter.getValues());
        }

        // 否则使用常规匹配逻辑
        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> rowData : excelData) {
            if (matchesAllFilters(rowData, criteria)) {
                filtered.add(rowData);
            }
        }

        return filtered;
    }

    /**
     * 根据行号提取数据
     */
    private static List<Map<String, Object>> extractRowsByNumbers(
            List<Map<String, Object>> excelData, List<String> rowNumbers) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (String rowNumStr : rowNumbers) {
            try {
                // 解析行号（从 1 开始计数）
                int rowNum = Integer.parseInt(rowNumStr.trim());

                // 转换为索引（从 0 开始）
                int index = rowNum - 1;

                // 检查索引是否有效
                if (index >= 0 && index < excelData.size()) {
                    result.add(excelData.get(index));
                    log.debug("提取第 {} 行数据：{}", rowNum, excelData.get(index));
                } else {
                    log.warn("行号 {} 超出数据范围（共 {} 行），已跳过", rowNum, excelData.size());
                }
            } catch (NumberFormatException e) {
                log.warn("无效的行号格式：{}，已跳过", rowNumStr);
            }
        }

        return result;
    }

    /**
     * 检查数据是否满足所有筛选条件
     */
    private static boolean matchesAllFilters(Map<String, Object> rowData, FilterCriteria criteria) {
        for (FilterCondition condition : criteria.getFilters()) {
            if (!matchesSingleFilter(rowData, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查数据是否满足单个筛选条件
     */
    private static boolean matchesSingleFilter(Map<String, Object> rowData, FilterCondition condition) {
        String column = condition.getColumn();
        int mode = condition.getMode();
        List<String> values = condition.getValues();

        Object cellValue = rowData.get(column);
        if (cellValue == null) {
            return false;
        }

        String cellStr = cellValue.toString().trim();

        switch (mode) {
            case 0: // 单项匹配
                return values.stream().anyMatch(v -> v.equalsIgnoreCase(cellStr));

            case 1: // 比较匹配（>, <, >=, <=）
                if (values.size() < 2)
                    return false;
                String compareValue = values.get(0);
                String operator = values.get(1);
                return compareValues(cellStr, compareValue, operator);

            case 2: // 范围匹配
                if (values.size() < 2)
                    return false;
                return isInRange(cellStr, values.get(0), values.get(1));

            case 3: // 多选匹配
                return values.stream().anyMatch(v -> v.equalsIgnoreCase(cellStr));

            case 4: // 行号匹配
                // 行号匹配在 applyFilterCriteria 方法中已特殊处理，这里不会执行到
                return false;

            default:
                return false;
        }
    }

    /**
     * 比较两个值
     */
    private static boolean compareValues(String cellValue, String compareValue, String operator) {
        try {
            // 尝试数值比较
            double cellNum = Double.parseDouble(cellValue);
            double compareNum = Double.parseDouble(compareValue);

            switch (operator) {
                case ">":
                    return cellNum > compareNum;
                case "<":
                    return cellNum < compareNum;
                case ">=":
                    return cellNum >= compareNum;
                case "<=":
                    return cellNum <= compareNum;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            // 尝试日期比较
            if (DateCompareUtil.isDate(cellValue) && DateCompareUtil.isDate(compareValue)) {
                int cmp = DateCompareUtil.compare(cellValue, compareValue);
                switch (operator) {
                    case ">":
                        return cmp > 0;
                    case "<":
                        return cmp < 0;
                    case ">=":
                        return cmp >= 0;
                    case "<=":
                        return cmp <= 0;
                    default:
                        return false;
                }
            }
            // 字符串比较
            return cellValue.compareTo(compareValue) > 0;
        }
    }

    /**
     * 检查值是否在范围内
     */
    private static boolean isInRange(String cellValue, String minVal, String maxVal) {
        try {
            double cellNum = Double.parseDouble(cellValue);
            double minNum = Double.parseDouble(minVal);
            double maxNum = Double.parseDouble(maxVal);
            return cellNum >= minNum && cellNum <= maxNum;
        } catch (NumberFormatException e) {
            // 尝试日期范围
            if (DateCompareUtil.isDate(cellValue) && DateCompareUtil.isDate(minVal) && DateCompareUtil.isDate(maxVal)) {
                return DateCompareUtil.compare(cellValue, minVal) >= 0 &&
                        DateCompareUtil.compare(cellValue, maxVal) <= 0;
            }
            // 字符串范围
            return cellValue.compareTo(minVal) >= 0 && cellValue.compareTo(maxVal) <= 0;
        }
    }

    /**
     * 清理临时文件并移动结果到 result 区
     */
    private static void cleanupAndMoveResult(String resultFileId, Set<String> tempFileIds,
            String userId, FileService fileService) throws Exception {
        // 先移动结果文件到 result 区
        try {
            fileService.moveFileToSection(resultFileId, "result", userId);
            log.info("结果文件已移动到 result 区：{}", resultFileId);
        } catch (Exception e) {
            log.warn("移动结果文件失败，保留在 temp 区：{}", resultFileId, e);
            throw e; // 如果移动失败，抛出异常，不删除临时文件
        }

        // 从 tempFileIds 中移除 resultFileId，避免被误删
        tempFileIds.remove(resultFileId);

        // 删除其他临时文件
        for (String fileId : tempFileIds) {
            try {
                fileService.deleteFileById(fileId, userId);
                log.debug("已删除临时文件：{}", fileId);
            } catch (Exception e) {
                log.warn("删除临时文件失败：{}", fileId, e);
            }
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 筛选条件
     */
    static class FilterCriteria {
        private List<FilterCondition> filters = new ArrayList<>();

        public List<FilterCondition> getFilters() {
            return filters;
        }

        public void addFilter(String column, int mode, List<String> values) {
            filters.add(new FilterCondition(column, mode, values));
        }
    }

    /**
     * 单个筛选条件
     */
    static class FilterCondition {
        private String column;
        private int mode;
        private List<String> values;

        public FilterCondition(String column, int mode, List<String> values) {
            this.column = column;
            this.mode = mode;
            this.values = values;
        }

        public String getColumn() {
            return column;
        }

        public int getMode() {
            return mode;
        }

        public List<String> getValues() {
            return values;
        }
    }
}
