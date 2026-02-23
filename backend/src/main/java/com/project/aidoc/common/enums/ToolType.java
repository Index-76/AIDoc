package com.project.aidoc.common.enums;

/**
 * 工具类型枚举
 */
public enum ToolType {
    /**
     * 不使用工具
     */
    NONE(0, "不使用工具"),

    /**
     * 目录查看工具
     */
    DIRECTORY_VIEW(1, "目录查看"),

    /**
     * 内容总结工具
     */
    CONTENT_SUMMARY(2, "内容总结"),

    /**
     * 格式转换工具
     */
    FORMAT_CONVERSION(3, "格式转换"),

    /**
     * 智能填表工具
     */
    SMART_FILL(4, "智能填表"),

    /**
     * 智能修改工具
     */
    SMART_MODIFY(5, "智能修改");

    private final int code;
    private final String description;

    ToolType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取工具类型
     */
    public static ToolType fromCode(int code) {
        for (ToolType type : ToolType.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * 根据关键词判断工具类型
     */
    public static ToolType fromKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return NONE;
        }

        String lowerKeyword = keyword.toLowerCase();

        // 目录查看关键词 - 扩展更多表达方式
        if (lowerKeyword.contains("查看目录") || 
            lowerKeyword.contains("目录查看") || 
            lowerKeyword.contains("目录结构") || 
            lowerKeyword.contains("文件列表") ||
            lowerKeyword.contains("查看文件") ||
            lowerKeyword.contains("文件目录")) {
            return DIRECTORY_VIEW;
        } else if (lowerKeyword.contains("总结") || lowerKeyword.contains("概括") || lowerKeyword.contains("摘要")) {
            return CONTENT_SUMMARY;
        } else if (lowerKeyword.contains("转换") || lowerKeyword.contains("格式") || lowerKeyword.contains("转")) {
            return FORMAT_CONVERSION;
        } else if (lowerKeyword.contains("填表")) {
            return SMART_FILL;
        } else if (lowerKeyword.contains("自动修改") || lowerKeyword.contains("自动编辑") || lowerKeyword.contains("智能调整")) {
            return SMART_MODIFY;
        }

        return NONE;
    }
}