package com.moodtree.app.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 日期工具：服务端 date 是 LocalDate 的 ISO 串（yyyy-MM-dd），at/updated_at 是带时区的 ISO 串。
 *  minSdk 26 直接用 java.time。 */
public class Dates {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("M月d日");

    /** 今天 yyyy-MM-dd */
    public static String today() {
        return LocalDate.now().format(ISO_DATE);
    }

    /** 当前 OffsetDateTime 的完整 ISO 串，带本机时区偏移，作为 updated_at/at */
    public static String nowIso() {
        return OffsetDateTime.now().toString();
    }

    /** 把 yyyy-MM-dd 解析成 LocalDate，失败返回 null */
    public static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDate.parse(s, ISO_DATE); }
        catch (DateTimeParseException e) { return null; }
    }

    /** 把任意 ISO 时间串解析成 OffsetDateTime，失败返回 null（at 字段可空） */
    public static OffsetDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return OffsetDateTime.parse(s); }
        catch (DateTimeParseException e) { return null; }
    }

    /** yyyy-MM-dd → "M月d日" 展示用 */
    public static String display(String isoDate) {
        LocalDate d = parseDate(isoDate);
        return d == null ? isoDate : d.format(DISPLAY);
    }

    /** 把 at（带时区 ISO）展示成 "HH:mm"；解析失败返回空串 */
    public static String timeOfDay(String at) {
        OffsetDateTime t = parseDateTime(at);
        return t == null ? "" : t.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** 从 ISO 时间串解析出"从午夜开始的分钟数"(0~1439)，用于周视图纵向定位 */
    public static int minutesOfDay(String at) {
        OffsetDateTime t = parseDateTime(at);
        return t == null ? 0 : t.getHour() * 60 + t.getMinute();
    }

    /** YearMonth → "yyyy-MM-%" 前缀，给 listForMonth 的 LIKE 查询用（必须带 % 通配符！） */
    public static String monthPrefix(YearMonth ym) {
        return ym.toString() + "-%";   // YearMonth.toString() 形如 "2026-07"，加 '-' 后为 "2026-07-%"
    }

    /** YearMonth → "2026年7月" 展示用 */
    public static String monthLabel(YearMonth ym) {
        return ym.getYear() + "年" + ym.getMonthValue() + "月";
    }
}
