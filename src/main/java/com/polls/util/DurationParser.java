package com.polls.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhd])");

    /** 解析如 "3d", "12h", "30m", "90s" 为毫秒，不合法返回 -1 */
    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) return -1;
        Matcher m = PATTERN.matcher(input.trim().toLowerCase());
        if (!m.matches()) return -1;
        try {
            long value = Long.parseLong(m.group(1));
            if (value <= 0) return -1;
            long multiplier = switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> -1L;
            };
            return multiplier < 0 ? -1 : Math.multiplyExact(value, multiplier);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    /** 将毫秒格式化为可读字符串，如 "2天3小时" */
    public static String format(long millis) {
        if (millis <= 0) return "已结束";
        long days    = TimeUnit.MILLISECONDS.toDays(millis);
        long hours   = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("天");
        if (hours > 0)   sb.append(hours).append("小时");
        if (minutes > 0 || sb.isEmpty()) sb.append(minutes).append("分钟");
        return sb.toString();
    }
}
