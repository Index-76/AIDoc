package com.project.aidoc.common.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateCompareUtil {

    /**
     * 比较两个日期的早晚
     * 
     * @param date1 日期字符串 1
     * @param date2 日期字符串 2
     * @return date1 早于 date2 返回 0，否则返回 1
     */
    public static int compare(String date1, String date2) {
        try {
            Date d1 = parseDate(date1);
            Date d2 = parseDate(date2);

            // d1 早于 d2 返回 0，否则返回 1
            return d1.before(d2) ? 0 : 1;
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式解析错误：" + e.getMessage());
        }
    }

    /**
     * 判断字符串是否是有效的日期
     * 
     * @param dateStr 待检查的字符串
     * @return 如果是有效的日期格式返回 true，否则返回 false
     */
    public static boolean isDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return false;
        }

        try {
            parseDate(dateStr);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * 解析日期字符串，支持多种格式
     * 
     * @param dateStr 日期字符串
     * @return 解析后的 Date 对象
     * @throws ParseException 解析失败时抛出异常
     */
    private static Date parseDate(String dateStr) throws ParseException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("日期字符串不能为空");
        }

        // 去除首尾空格
        String normalized = dateStr.trim();

        // 定义支持的日期格式（包含带空格的变体）
        String[] patterns = {
                "yyyy/M/d", // 2020/7/1
                "yyyy/MM/dd", // 2020/07/01
                "yyyy 年 M 月 d 日", // 2020 年 7 月 1 日
                "yyyy 年 MM 月 dd 日", // 2020 年 07 月 01 日
                "yyyy.M.d", // 2020.7.1
                "yyyy.MM.dd", // 2020.07.01
                "yyyy-MM-dd", // 2020-07-01
                "yyyy/MM/dd", // 2020/07/01
                "yyyy 年 M 月 d 日", // 2020 年 7 月 1 日 (带空格)
                "yyyy 年 M 月 d 日", // 2020 年 7 月 1 日 (带空格)
                "yyyy . M . d", // 2020 . 7 . 1 (带空格)
                "yyyy / M / d", // 2020 / 7 / 1 (带空格)
                "yyyy - M - d", // 2020 - 07 - 01 (带空格)
        };

        // 尝试不同的日期格式进行解析
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false); // 设置为非宽松模式，严格匹配日期
                return sdf.parse(normalized);
            } catch (ParseException e) {
                // 继续尝试下一个格式
                continue;
            }
        }

        throw new ParseException("无法识别的日期格式：" + dateStr, 0);
    }
}
