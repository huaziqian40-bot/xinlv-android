package com.moodtree.app.model;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** 主题引擎：4 套预设（暖阳/夜晚/薄荷/樱花）+ 3 色自定义（背景/卡片/强调色）。
 *  颜色用静态字段存，apply() 切换后整页重渲染生效。
 *  提供 drawable 工厂方法，所有 UI 组件通过程序化背景而不是 XML drawable 引用颜色，
 *  确保主题切换后所有元素同步变色。
 *
 *  3 色体系：BG（背景色）、CARD（卡片/功能框色）、ACCENT（按钮/强调色）
 *  INK / INK_SOFT 根据 BG 自动计算明暗（浅底深字、深底浅字）。
 *  DIVIDER 从 CARD 与 BG 的中间色自动计算。 */
public class Theme {

    public static int BG, CARD, INK, INK_SOFT, ACCENT, ACCENT_D, DANGER, DIVIDER;

    // 三个可自定义的颜色（预设值 + 用户覆盖）
    public static String presetBg, presetCard, presetAccent;

    /** 4 套预设：{id, 名称, 预览色, bg, card, accent} */
    public static final String[][] PRESETS = {
            {"warm",   "暖阳", "#7d9b76", "#f6f1e7", "#fffdf8", "#7d9b76"},
            {"night",  "夜晚", "#93b18b", "#26241f", "#35322b", "#7d9b76"},
            {"mint",   "薄荷", "#5ea07c", "#eef6f1", "#fbfffc", "#5ea07c"},
            {"sakura", "樱花", "#d18a9a", "#faf0f2", "#fffafa", "#d18a9a"},
    };

    // ---- 主题数据字段（供外部读取） ----
    // PRESETS 中每项的索引偏移
    private static final int IDX_BG = 3;
    private static final int IDX_CARD = 4;
    private static final int IDX_ACCENT = 5;

    /** 应用主题：预设 + 3 色自定义覆盖。
     *  @param id 预设 id（warm/night/mint/sakura），为 null 时默认 warm
     *  @param bgHex    自定义背景色，null 或空串 = 用预设值
     *  @param cardHex  自定义卡片色，null 或空串 = 用预设值
     *  @param accentHex 自定义强调色，null 或空串 = 用预设值 */
    public static void apply(String id, String bgHex, String cardHex, String accentHex) {
        // 定位预设
        String pid = (id == null) ? "warm" : id;
        String[] preset = null;
        for (String[] p : PRESETS) {
            if (p[0].equals(pid)) { preset = p; break; }
        }
        if (preset == null) preset = PRESETS[0];

        // 保存预设值（供 ColorPicker 作为基准）
        presetBg = preset[IDX_BG];
        presetCard = preset[IDX_CARD];
        presetAccent = preset[IDX_ACCENT];

        // 用户覆盖 or 预设
        String bgStr = (bgHex != null && !bgHex.isEmpty()) ? bgHex : presetBg;
        String cardStr = (cardHex != null && !cardHex.isEmpty()) ? cardHex : presetCard;
        String accentStr = (accentHex != null && !accentHex.isEmpty()) ? accentHex : presetAccent;

        BG = parse(bgStr);
        CARD = parse(cardStr);
        ACCENT = parse(accentStr);
        ACCENT_D = darker(ACCENT);

        // 自动派生颜色
        INK = textColorFor(BG);
        INK_SOFT = softTextColorFor(BG);
        DIVIDER = dividerColor(BG, CARD);
        DANGER = parse("#c9706a");
    }

    /** 兼容旧版：只传 accentHex，bg/card 用预设值。
     *  此方法保留给现有调用方（MeFragment.applyTheme），新版逐步迁移。 */
    public static void apply(String id, String accentHex) {
        apply(id, null, null, accentHex);
    }

    /** 应用纯预设（无自定义覆盖） */
    public static void applyPreset(String id) {
        apply(id, null, null, null);
    }

    // ========== 自动颜色派生 ==========

    /** 根据背景色返回最佳文字色（浅底→深色字，深底→浅色字） */
    private static int textColorFor(int bg) {
        double lum = luminance(bg);
        return lum > 0.5 ? parse("#111111") : parse("#f0ece4");
    }

    /** 根据背景色返回柔和文字色（用于提示文字、次要信息） */
    private static int softTextColorFor(int bg) {
        double lum = luminance(bg);
        return lum > 0.5 ? parse("#555555") : parse("#c0b8a8");
    }

    /** 分隔线颜色 = BG 与 CARD 的中间色，保证在任何主题下都能看见 */
    private static int dividerColor(int bg, int card) {
        return blend(bg, card, 0.5f);
    }

    /** 计算颜色亮度（0~1），符合 WCAG 相对亮度近似 */
    private static double luminance(int c) {
        double r = linearize(Color.red(c) / 255.0);
        double g = linearize(Color.green(c) / 255.0);
        double b = linearize(Color.blue(c) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** 将两色按比例混合 */
    public static int blend(int c1, int c2, float ratio) {
        int a = (int) (Color.alpha(c1) * (1 - ratio) + Color.alpha(c2) * ratio);
        int r = (int) (Color.red(c1) * (1 - ratio) + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * (1 - ratio) + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * (1 - ratio) + Color.blue(c2) * ratio);
        return Color.argb(a, r, g, b);
    }

    // ========== Drawable 工厂 ==========

    /** 创建卡片背景 drawable（CARD 色 + 14dp 圆角） */
    public static GradientDrawable createCardBg() {
        return createCardBg(14);
    }

    /** 创建卡片背景 drawable（指定圆角半径 dp，需乘 density） */
    public static GradientDrawable createCardBg(int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(CARD);
        gd.setCornerRadius(radiusDp * 1f);   // 调用方需乘 density
        return gd;
    }

    /** 创建卡片背景 drawable（指定圆角半径 dp，自动乘 density） */
    public static GradientDrawable createCardBg(float density, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(CARD);
        gd.setCornerRadius(radiusDp * density);
        return gd;
    }

    /** 创建输入框背景 drawable（输入框底色 + 12dp 圆角 + 内边距） */
    public static GradientDrawable createInputBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(inputBgColor());
        gd.setCornerRadius(12);
        return gd;
    }

    /** 输入框底色：比 CARD 稍暗（浅主题）或稍亮（深主题），形成微妙对比 */
    public static int inputBgColor() {
        double lum = luminance(BG);
        return lum > 0.5 ? blend(CARD, BG, 0.5f) : blend(CARD, parse("#4a4a4a"), 0.5f);
    }

    /** 创建主按钮背景 drawable（ACCENT 色 + 12dp 圆角） */
    public static GradientDrawable createPrimaryButton() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ACCENT);
        gd.setCornerRadius(12);
        return gd;
    }

    /** 创建副按钮背景 drawable（透明 + ACCENT 边框 + 12dp 圆角） */
    public static GradientDrawable createOutlineButton() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.TRANSPARENT);
        gd.setStroke(1, ACCENT);
        gd.setCornerRadius(12);
        return gd;
    }

    /** 创建 emoji 圆形背景（输入框底色 + 12dp 圆角） */
    public static GradientDrawable createEmojiBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(inputBgColor());
        gd.setCornerRadius(12);
        return gd;
    }

    /** 创建 Chip 选中态背景（ACCENT 色 + 20dp 圆角） */
    public static GradientDrawable createChipActive() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ACCENT);
        gd.setCornerRadius(20);
        return gd;
    }

    /** 创建圆角矩形 drawable（指定颜色和圆角 dp） */
    public static GradientDrawable createRoundedRect(int color, float cornerRadiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(cornerRadiusDp);
        return gd;
    }

    // ========== HSV 颜色工具（供 ColorPickerView 使用） ==========

    /** 将 int 颜色转为 HSV 数组 */
    public static float[] toHSV(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv;
    }

    /** 从 HSV 数组创建 int 颜色 */
    public static int fromHSV(float[] hsv) {
        return Color.HSVToColor(hsv);
    }

    /** 从色相（0-360）、饱和度（0-1）、明度（0-1）创建颜色 */
    public static int fromHSV(float hue, float saturation, float brightness) {
        return Color.HSVToColor(new float[]{hue, saturation, brightness});
    }

    /** 调整颜色亮度（factor < 1 变暗，> 1 变亮） */
    public static int adjustBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0, Math.min(1, hsv[2] * factor));
        return Color.HSVToColor(hsv);
    }

    /** 调整颜色饱和度（factor < 1 降低，> 1 增加） */
    public static int adjustSaturation(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0, Math.min(1, hsv[1] * factor));
        return Color.HSVToColor(hsv);
    }

    /** 获取色相环上的一系列色相值（用于色相条显示） */
    public static int[] hueSpectrum() {
        int[] colors = new int[360];
        for (int i = 0; i < 360; i++) {
            colors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }
        return colors;
    }

    /** 生成饱和度-明度 2D 平面（给定色相） */
    public static int[][] saturationBrightnessGrid(int hue, int steps) {
        int[][] grid = new int[steps][steps];
        for (int s = 0; s < steps; s++) {
            for (int v = 0; v < steps; v++) {
                grid[s][v] = Color.HSVToColor(new float[]{
                        hue,
                        (float) s / (steps - 1),
                        (float) v / (steps - 1)
                });
            }
        }
        return grid;
    }

    // ========== 基础工具 ==========

    /** 把颜色压暗一些（用于强调色的深色变体，按钮文字阴影等） */
    public static int darker(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] *= 0.78f;
        return Color.HSVToColor(hsv);
    }

    /** int 颜色转 #RRGGBB（给 SharedPreferences 存自定义颜色用） */
    public static String toHex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    /** 格式化颜色为 #RRGGBB 或 #AARRGGBB */
    public static String toHexWithAlpha(int c) {
        return String.format("#%08X", c);
    }

    /** 解析十六进制颜色，失败返回 GRAY */
    public static int parse(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }

    /** 判断颜色是否为深色（亮度 < 0.5） */
    public static boolean isDark(int color) {
        return luminance(color) < 0.5;
    }

    /** 判断当前主题是否为深色主题 */
    public static boolean isDarkTheme() {
        return isDark(BG);
    }
}