package com.project.aidoc.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.ApiService;
import com.project.aidoc.service.UserConfigService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Excel 数据处理工具类
 * 职责：
 * - 解析 Excel 为结构化数据
 * - 生成 Excel 摘要（用于 AI 决策）
 * - 调用 AI 解析用户筛选条件
 * - 根据筛选条件过滤数据
 */
public class ExcelDataProcessor {
    private static final Logger log = LoggerFactory.getLogger(ExcelDataProcessor.class);

    // ==================== 内部数据结构 ====================

    /**
     * Excel 摘要信息（用于文件映射决策）
     */
    public static class ExcelSummary {
        private int sheetNum;
        private List<SheetSummary> sheets;

        public int getSheetNum() { return sheetNum; }
        public void setSheetNum(int sheetNum) { this.sheetNum = sheetNum; }
        public List<SheetSummary> getSheets() { return sheets; }
        public void setSheets(List<SheetSummary> sheets) { this.sheets = sheets; }

        public static class SheetSummary {
            private int sheetIndex;
            private String sheetName;
            private List<String> headers;
            private List<List<String>> sampleRows; // 前5行样本

            public int getSheetIndex() { return sheetIndex; }
            public void setSheetIndex(int sheetIndex) { this.sheetIndex = sheetIndex; }
            public String getSheetName() { return sheetName; }
            public void setSheetName(String sheetName) { this.sheetName = sheetName; }
            public List<String> getHeaders() { return headers; }
            public void setHeaders(List<String> headers) { this.headers = headers; }
            public List<List<String>> getSampleRows() { return sampleRows; }
            public void setSampleRows(List<List<String>> sampleRows) { this.sampleRows = sampleRows; }
        }
    }

    /**
     * 筛选条件（与 ExcelToTemplate 中原定义一致）
     */
    public static class FilterCriteria {
        private List<FilterCondition> filters = new ArrayList<>();

        public List<FilterCondition> getFilters() { return filters; }
        public void setFilters(List<FilterCondition> filters) { this.filters = filters; }
        public void addFilter(String column, int mode, List<String> values) {
            filters.add(new FilterCondition(column, mode, values));
        }
    }

    /**
     * 单个筛选条件
     */
    public static class FilterCondition {
        private String column;
        private int mode;
        private List<String> values;

        public FilterCondition() {}
        public FilterCondition(String column, int mode, List<String> values) {
            this.column = column;
            this.mode = mode;
            this.values = values;
        }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }
        public int getMode() { return mode; }
        public void setMode(int mode) { this.mode = mode; }
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
    }

    // ==================== 核心方法 ====================

    /**
     * 将 Excel 字节数组解析为行数据列表
     * 默认第一行为表头，数据行从第二行开始；空行跳过
     * @param content Excel 文件字节内容
     * @return 行数据列表，每行是一个 Map（列名 -> 单元格值，统一转换为 String）
     * @throws Exception 解析失败时抛出
     */
    public static List<Map<String, Object>> parseExcelToData(byte[] content) throws Exception {
        List<Map<String, Object>> allData = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
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
                    if (row == null || isRowEmpty(row)) continue;
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
        log.info("解析 Excel 完成，共 {} 行数据", allData.size());
        return allData;
    }

    /**
     * 生成 Excel 摘要信息（用于 AI 文件映射决策）
     * @param content Excel 字节内容
     * @param fileId  文件 ID（仅用于日志）
     * @return ExcelSummary 对象
     * @throws Exception 生成失败时抛出
     */
    public static ExcelSummary generateExcelSummary(byte[] content, String fileId) throws Exception {
        ExcelSummary summary = new ExcelSummary();
        List<ExcelSummary.SheetSummary> sheetSummaries = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            int sheetNum = workbook.getNumberOfSheets();
            summary.setSheetNum(sheetNum);

            for (int i = 0; i < sheetNum; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                ExcelSummary.SheetSummary sheetSummary = new ExcelSummary.SheetSummary();
                sheetSummary.setSheetIndex(i);
                sheetSummary.setSheetName(sheet.getSheetName());

                // 提取表头（使用 ExcelSheetExtractorUtil 确保表头合并及去重）
                List<Map<String, Object>> extracted = ExcelSheetExtractorUtil.extractSheets(content, 0, 1, null);
                if (i < extracted.size()) {
                    sheetSummary.setHeaders((List<String>) extracted.get(i).get("headers"));
                } else {
                    // 降级处理：手动提取第一行作为表头
                    Row headerRow = sheet.getRow(0);
                    if (headerRow != null) {
                        List<String> headers = new ArrayList<>();
                        for (Cell cell : headerRow) {
                            headers.add(getCellValueAsString(cell));
                        }
                        sheetSummary.setHeaders(makeUniqueHeaders(headers));
                    } else {
                        sheetSummary.setHeaders(new ArrayList<>());
                    }
                }

                // 提取前5行样本数据
                List<List<String>> sampleRows = new ArrayList<>();
                int sampleCount = 0;
                int maxSamples = Integer.parseInt(System.getProperty("excel.sample.rows", "5"));
                int lastRowNum = sheet.getLastRowNum();
                for (int r = 1; r <= lastRowNum && sampleCount < maxSamples; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) continue;
                    List<String> rowValues = new ArrayList<>();
                    for (int c = 0; c < sheetSummary.getHeaders().size(); c++) {
                        Cell cell = row.getCell(c);
                        rowValues.add(cell != null ? getCellValueAsString(cell) : "");
                    }
                    sampleRows.add(rowValues);
                    sampleCount++;
                }
                sheetSummary.setSampleRows(sampleRows);
                sheetSummaries.add(sheetSummary);
            }
        }
        summary.setSheets(sheetSummaries);
        log.info("生成 Excel 摘要完成，文件 ID: {}, 工作表数: {}", fileId, summary.getSheetNum());
        return summary;
    }

    /**
     * 调用 AI 解析用户消息，提取筛选条件
     * @param userMessage      用户输入的自然语言描述
     * @param sampleData       样本数据（前几行，用于提示词构建）
     * @param headers          目标表头列表
     * @param primaryKeyHeader 主键列名（可为 null）
     * @param userId           用户 ID
     * @param userConfigService 用户配置服务
     * @param apiService       API 服务
     * @param templateFileName 模板文件名
     * @param currentSheetName 当前工作表/表格名称
     * @return 筛选条件对象
     * @throws Exception 解析失败或 AI 调用失败时抛出
     */
    public static FilterCriteria parseFilterCriteria(
            String userMessage,
            List<Map<String, Object>> sampleData,
            List<String> headers,
            String primaryKeyHeader,
            String userId,
            UserConfigService userConfigService,
            ApiService apiService,
            String templateFileName,
            String currentSheetName) throws Exception {

        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null) {
            log.warn("未找到用户配置，返回空筛选条件（不过滤）");
            return new FilterCriteria();
        }

        String prompt = buildFilterCriteriaPrompt(userMessage, sampleData, headers, primaryKeyHeader,
                templateFileName, currentSheetName);
        String aiResponse = callAIWithRetry(prompt, userConfig, apiService);
        System.out.println("AI 解析结果：" + aiResponse);
        return parseFilterCriteriaFromAIResponse(aiResponse);
    }

    /**
     * 根据筛选条件过滤数据
     * @param data     原始数据（由 parseExcelToData 返回）
     * @param criteria 筛选条件
     * @return 过滤后的数据列表
     */
    public static List<Map<String, Object>> applyFilter(List<Map<String, Object>> data, FilterCriteria criteria) {
        if (criteria == null || criteria.getFilters().isEmpty()) {
            return data; // 无筛选条件，返回全部数据
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
            return extractRowsByNumbers(data, rowNumberFilter.getValues());
        }

        // 否则使用常规匹配逻辑
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> rowData : data) {
            if (matchesAllFilters(rowData, criteria)) {
                filtered.add(rowData);
            }
        }
        return filtered;
    }

    // ==================== 私有辅助方法 ====================

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:    return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 统一日期格式为 yyyy/M/d
                    Date date = cell.getDateCellValue();
                    LocalDateTime localDateTime = date.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    return localDateTime.format(DateTimeFormatter.ofPattern("yyyy/M/d"));
                } else {
                    double num = cell.getNumericCellValue();
                    return (num == (long) num) ? String.valueOf((long) num) : String.valueOf(num);
                }
            case BOOLEAN:   return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            default: return "";
        }
    }

    private static boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将日期字符串解析为 LocalDate
     * 支持两种常见格式：yyyy/M/d 和 yyyy-MM-dd
     */
    private static LocalDate parseToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        // 支持两种常见格式：yyyy/M/d 和 yyyy-MM-dd
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }
        return null;
    }

    private static List<String> makeUniqueHeaders(List<String> rawHeaders) {
        List<String> unique = new ArrayList<>();
        Map<String, Integer> counter = new HashMap<>();
        for (String h : rawHeaders) {
            if (h == null) h = "";
            int count = counter.getOrDefault(h, 0);
            if (count > 0) {
                unique.add(h + "_" + (count + 1));
            } else {
                unique.add(h);
            }
            counter.put(h, count + 1);
        }
        return unique;
    }

    // ---------- AI 筛选条件解析相关 ----------

    private static String buildFilterCriteriaPrompt(
            String userMessage,
            List<Map<String, Object>> sampleData,
            List<String> headers,
            String primaryKeyHeader,
            String templateFileName,
            String currentSheetName) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能填表助手，需要从用户消息中提取数据筛选条件。\n\n");

        // 添加上下文信息
        prompt.append("【当前模板文件名】\n").append(templateFileName).append("\n\n");
        prompt.append("【当前工作表/表格名称】\n").append(currentSheetName).append("\n\n");

        prompt.append("【用户消息】\n").append(userMessage).append("\n\n");

        // 提供列信息
        prompt.append("【可用数据列】\n");
        for (String h : headers) {
            prompt.append("- ").append(h);
            if (h.equals(primaryKeyHeader)) prompt.append(" (主键)");
            prompt.append("\n");
        }
        prompt.append("\n");

        // 提供样本数据（前 3 行）
        if (!sampleData.isEmpty()) {
            prompt.append("【数据示例（前 ").append(sampleData.size()).append(" 行）】\n");
            for (int i = 0; i < sampleData.size(); i++) {
                prompt.append("行").append(i + 1).append(": ").append(sampleData.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("【任务要求】\n");
        prompt.append("用户消息中可能包含多个表格的要求，每个表格由其名称（如表一、表二）标识。\n");
        prompt.append("请根据 **当前工作表/表格名称**（即“").append(currentSheetName).append("”）提取对应的筛选条件，忽略其他表格的描述。\n");
        prompt.append("例如，如果当前表格是“表一”，消息中对应描述为“表一：监测时间：... 城市：德州市”，则应提取监测时间和城市两个条件。\n\n");
        prompt.append("请以 JSON 格式返回筛选条件，格式如下：\n");
        prompt.append("{\n");
        prompt.append("  \"filters\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"column\": \"列名\",\n");
        prompt.append("      \"mode\": 0,  // 0=单项匹配，1=比较，2=范围，3=多选，4=行号\n");
        prompt.append("      \"values\": [\"值1\"]\n");
        prompt.append("    },\n");
        prompt.append("    ... // 可多个条件，条件之间为 AND 关系\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("重要：\n");
        prompt.append("1. 只返回 JSON，不要包含任何说明文字、代码块或多余字符。\n");
        prompt.append("2. 若无法确定当前表格对应的条件，请返回 {\"filters\": []}。\n");


        return prompt.toString();
    }

    private static String callAIWithRetry(String prompt, UserConfig userConfig, ApiService apiService) throws Exception {
        int maxRetries = 3;
        long initialDelay = 1000; // 1秒
        long wait = initialDelay;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = apiService.callExternalApi(
                        userConfig.getSiliconFlowBaseUrl(),
                        userConfig.getSiliconFlowApiKey(),
                        prompt,
                        userConfig.getAnalysisModelName()
                );
                if (response != null && !response.trim().isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("AI 调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(wait);
                    wait *= 2;
                }
            }
        }
        throw new Exception("AI 调用失败，已达到最大重试次数");
    }

    private static FilterCriteria parseFilterCriteriaFromAIResponse(String aiResponse) throws Exception {
        FilterCriteria criteria = new FilterCriteria();
        try {
            String jsonContent = extractJsonFromResponse(aiResponse);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> filters = (List<Map<String, Object>>) jsonMap.getOrDefault("filters", new ArrayList<>());

            for (Map<String, Object> filter : filters) {
                String column = (String) filter.getOrDefault("column", "");
                int mode = ((Number) filter.getOrDefault("mode", 0)).intValue();
                List<String> values = (List<String>) filter.getOrDefault("values", new ArrayList<>());
                criteria.addFilter(column, mode, values);
            }
        } catch (Exception e) {
            log.warn("解析 AI 响应失败，返回空筛选条件: {}", e.getMessage());
        }
        return criteria;
    }

    private static String extractJsonFromResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response.trim();
    }

    // ---------- 过滤逻辑辅助方法 ----------

    private static List<Map<String, Object>> extractRowsByNumbers(
            List<Map<String, Object>> data, List<String> rowNumbers) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String rowNumStr : rowNumbers) {
            try {
                int rowNum = Integer.parseInt(rowNumStr.trim());
                int index = rowNum - 1; // 用户说的行号通常从1开始
                if (index >= 0 && index < data.size()) {
                    result.add(data.get(index));
                } else {
                    log.warn("行号 {} 超出数据范围（共 {} 行），已跳过", rowNum, data.size());
                }
            } catch (NumberFormatException e) {
                log.warn("无效的行号格式：{}，已跳过", rowNumStr);
            }
        }
        return result;
    }

    private static boolean matchesAllFilters(Map<String, Object> rowData, FilterCriteria criteria) {
        for (FilterCondition condition : criteria.getFilters()) {
            if (!matchesSingleFilter(rowData, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSingleFilter(Map<String, Object> rowData, FilterCondition condition) {
        String column = condition.getColumn();
        int mode = condition.getMode();
        List<String> values = condition.getValues();

        Object cellValue = rowData.get(column);
        if (cellValue == null) return false;
        String cellStr = cellValue.toString().trim();

        switch (mode) {
            case 0: // 单项匹配
            case 3: // 多选匹配（逻辑相同）
                return values.stream().anyMatch(v -> v.equalsIgnoreCase(cellStr));
            case 1: // 比较匹配
                if (values.size() < 2) return false;
                return compareValues(cellStr, values.get(0), values.get(1));
            case 2: // 范围匹配
                if (values.size() < 2) return false;
                return isInRange(cellStr, values.get(0), values.get(1));
            default:
                return false;
        }
    }

    private static boolean compareValues(String cellValue, String compareValue, String operator) {
        // 优先尝试数字比较
        try {
            double cellNum = Double.parseDouble(cellValue);
            double compareNum = Double.parseDouble(compareValue);
            switch (operator) {
                case ">":  return cellNum > compareNum;
                case "<":  return cellNum < compareNum;
                case ">=": return cellNum >= compareNum;
                case "<=": return cellNum <= compareNum;
                default:   return false;
            }
        } catch (NumberFormatException e) {
            // 尝试日期比较
            LocalDate cellDate = parseToLocalDate(cellValue);
            LocalDate compareDate = parseToLocalDate(compareValue);
            if (cellDate != null && compareDate != null) {
                int cmp = cellDate.compareTo(compareDate);
                switch (operator) {
                    case ">":  return cmp > 0;
                    case "<":  return cmp < 0;
                    case ">=": return cmp >= 0;
                    case "<=": return cmp <= 0;
                    default:   return false;
                }
            }
            // 最后回退到字符串比较（仅当都不是日期时）
            return cellValue.compareTo(compareValue) > 0;
        }
    }

    private static boolean isInRange(String cellValue, String minVal, String maxVal) {
        // 优先尝试数字范围
        try {
            double cellNum = Double.parseDouble(cellValue);
            double minNum = Double.parseDouble(minVal);
            double maxNum = Double.parseDouble(maxVal);
            return cellNum >= minNum && cellNum <= maxNum;
        } catch (NumberFormatException e) {
            // 尝试日期范围
            LocalDate cellDate = parseToLocalDate(cellValue);
            LocalDate minDate = parseToLocalDate(minVal);
            LocalDate maxDate = parseToLocalDate(maxVal);
            if (cellDate != null && minDate != null && maxDate != null) {
                return cellDate.compareTo(minDate) >= 0 && cellDate.compareTo(maxDate) <= 0;
            }
            // 最后回退到字符串比较（仅当都不是日期时）
            return cellValue.compareTo(minVal) >= 0 && cellValue.compareTo(maxVal) <= 0;
        }
    }
}
