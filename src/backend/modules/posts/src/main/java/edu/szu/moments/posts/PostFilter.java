package edu.szu.moments.posts;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public record PostFilter(Instant from, Instant until, String tag, String keyword,
                         Long authorId, int page, int pageSize) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT);

    public static PostFilter parse(String from, String to, String tag, String keyword,
                                   Long authorId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50
                || (long) (page - 1) * pageSize > Integer.MAX_VALUE) {
            throw PostFailure.invalid("分页参数超出范围");
        }
        if (authorId != null && authorId < 1) {
            throw PostFailure.invalid("作者 ID 必须为正整数");
        }
        LocalDate start = date(from);
        LocalDate end = date(to);
        if (start != null && end != null && start.isAfter(end)) {
            throw PostFailure.invalid("开始日期不能晚于结束日期");
        }
        tag = clean(tag, 20, "标签");
        keyword = clean(keyword, 100, "关键词");
        return new PostFilter(start == null ? null : start.atStartOfDay(ZONE).toInstant(),
            end == null ? null : end.plusDays(1).atStartOfDay(ZONE).toInstant(),
            tag, keyword, authorId, page, pageSize);
    }

    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw PostFailure.invalid("日期格式须为 YYYY-MM-DD");
            }
            LocalDate date = LocalDate.parse(value, DATE);
            if (date.getYear() < 1000 || date.getYear() > 9998) {
                throw PostFailure.invalid("日期超出支持范围");
            }
            return date;
        } catch (DateTimeParseException ex) {
            throw PostFailure.invalid("日期无效");
        }
    }

    private static String clean(String value, int max, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        if (clean.codePointCount(0, clean.length()) > max) {
            throw PostFailure.invalid(name + "过长");
        }
        return clean;
    }
}

