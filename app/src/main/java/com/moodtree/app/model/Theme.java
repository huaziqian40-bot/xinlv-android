package com.moodtree.app.model;

import android.graphics.Color;

/** 主题引擎：4 套预设（暖阳/夜晚/薄荷/樱花）+ 自定义强调色。
 *  颜色用静态字段存，apply() 切换后整页重渲染生效（与 Windows 端 Theme 思路一致）。
 *  安卓端用 int 颜色（Color.parseColor）。 */
public class Theme {

    public static int BG, CARD, INK, INK_SOFT, ACCENT, ACCENT_D, DANGER, DIVIDER;

    /** 4 套预设：{id, 名称, 预览色} */
    public static final String[][] PRESETS = {
            {"warm",   "暖阳", "#7d9b76"},
            {"night",  "夜晚", "#93b18b"},
            {"mint",   "薄荷", "#5ea07c"},
            {"sakura", "樱花", "#d18a9a"},
    };

    public static void apply(String id, String accentHex) {
        boolean custom = accentHex != null && !accentHex.isEmpty();
        int accent = custom ? parse(accentHex) : 0;

        switch (id == null ? "warm" : id) {
            case "night":
                BG = parse("#26241f"); CARD = parse("#35322b"); INK = parse("#ece7db");
                INK_SOFT = parse("#a09a8b"); DIVIDER = parse("#2e2b25"); DANGER = parse("#d08078");
                ACCENT = custom ? accent : parse("#7d9b76"); ACCENT_D = darker(ACCENT);
                break;
            case "mint":
                BG = parse("#eef6f1"); CARD = parse("#fbfffc"); INK = parse("#33403a");
                INK_SOFT = parse("#5e6e65"); DIVIDER = parse("#e0efe7"); DANGER = parse("#c9706a");
                ACCENT = custom ? accent : parse("#5ea07c"); ACCENT_D = parse("#4c8767");
                break;
            case "sakura":
                BG = parse("#faf0f2"); CARD = parse("#fffafa"); INK = parse("#43353a");
                INK_SOFT = parse("#7d6a72"); DIVIDER = parse("#f5e3e8"); DANGER = parse("#c9706a");
                ACCENT = custom ? accent : parse("#d18a9a"); ACCENT_D = parse("#b87383");
                break;
            default: // warm
                BG = parse("#f6f1e7"); CARD = parse("#fffdf8"); INK = parse("#4a453d");
                INK_SOFT = parse("#6f6655"); DIVIDER = parse("#e5dccb"); DANGER = parse("#c9706a");
                ACCENT = custom ? accent : parse("#7d9b76"); ACCENT_D = parse("#4c6b46");
                break;
        }
    }

    private static int parse(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }

    /** 把颜色压暗一些（用于强调色的深色变体，按钮文字阴影等） */
    private static int darker(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] *= 0.78f;
        return Color.HSVToColor(hsv);
    }

    /** int 颜色转 #RRGGBB（给 SharedPreferences 存自定义强调色用） */
    public static String toHex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }
}
