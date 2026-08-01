package com.moodtree.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moodtree.app.R;
import com.moodtree.app.model.Theme;
import com.moodtree.app.sync.SyncEngine;
import com.moodtree.app.util.Bg;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** 我的：连胜/徽章/总记录（在线 profile，离线用缓存；游客用本机统计）+ 设置区
 *  （主题预设 + 强调色取色 + 服务器地址 + 刷新目录 + 退出登录）。对齐 Windows MeView。 */
public class MeFragment extends BaseFragment implements Refreshable {

    private LinearLayout contentBox;
    private TextView tvState;
    private boolean loaded;
    private int animDelay;          // 递增动画延迟

    // 可选强调色（安卓没有 ColorPicker 控件，给一组常用色 + 自定义输入）
    private static final String[] ACCENT_SWATCHES = {
            "#7d9b76", "#5ea07c", "#d18a9a", "#7FA6E8", "#FF9F68",
            "#9BD1C6", "#F7A6C4", "#E8736B", "#8E94B8"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_me, container, false);
        themeBackground(root);
        contentBox = root.findViewById(R.id.contentBox);
        tvState = root.findViewById(R.id.tvState);
        return root;
    }

    @Override
    public void refresh() {
        if (loaded) return;
        loaded = true;
        if (!app().config().loggedIn()) {
            renderGuest();
            return;
        }
        Bg.run(() -> {
                    JsonObject p;
                    if (app().api().ping()) {
                        p = app().api().profile();
                        app().db().kvDao().set("profile_cache", p.toString());
                    } else {
                        String cached = app().db().kvDao().get("profile_cache");
                        p = cached == null ? null : JsonParser.parseString(cached).getAsJsonObject();
                    }
                    return p;
                },
                this::renderProfile,
                err -> tvState.setText("加载失败：" + err.getMessage()));
    }

    // ---------- 游客：本地统计 + 登录引导 ----------

    private void renderGuest() {
        contentBox.removeAllViews();
        animDelay = 0;
        // 本地统计涉及 Room 查库，挪到后台线程算，算完回主线程渲染
        Bg.run(() -> {
                    int streak = 0, total = 0;
                    try {
                        total = app().db().moodDao().countAlive();
                        List<String> dates = app().db().moodDao().listDistinctDates();
                        Set<String> set = new HashSet<>(dates);
                        LocalDate cursor = LocalDate.now();
                        if (!set.contains(cursor.toString())) cursor = cursor.minusDays(1);
                        while (set.contains(cursor.toString())) {
                            streak++;
                            cursor = cursor.minusDays(1);
                        }
                    } catch (Exception ignored) { }
                    return new int[]{streak, total};
                },
                stat -> renderGuestViews(stat[0], stat[1]),
                err -> renderGuestViews(0, 0));
    }

    /** 游客页 UI 渲染（主线程）。streak/total 来自后台统计。 */
    private void renderGuestViews(int streak, int total) {
        if (!isAdded()) return;
        contentBox.addView(streakCard(streak, "连续记录 · 共 " + total + " 条（本机）"));

        LinearLayout card = card();
        TextView hint = text("登录后：记录自动同步到云端、解锁徽章墙、多设备互通，已有记录不会丢",
                Theme.INK, 14);
        LinearLayout.LayoutParams hp = lp(lpM(), lpW(), 0, dp(8), 0, 0);
        hint.setLayoutParams(hp);
        Button loginBtn = primaryBtn("登录 / 注册");
        loginBtn.setOnClickListener(v -> {
            app().config().setToken("");
            app().config().setGuestMode(false);
            startActivity(new Intent(getActivity(), LoginActivity.class));
            if (getActivity() != null) getActivity().finish();
        });
        card.addView(hint);
        card.addView(loginBtn);
        contentBox.addView(card);

        contentBox.addView(buildSettings(true));
    }

    // ---------- 登录用户：连胜 + 统计 + 徽章 ----------

    private void renderProfile(JsonObject p) {
        if (!isAdded()) return;
        contentBox.removeAllViews();
        animDelay = 0;
        if (p == null) {
            tvState.setText("离线且暂无缓存数据，联网后这里会显示你的连胜和徽章");
            tvState.setVisibility(View.VISIBLE);
            contentBox.addView(buildSettings(false));
            return;
        }
        tvState.setVisibility(View.GONE);

        // 头像 + 用户名
        View profileHeader = createProfileHeader(p);
        contentBox.addView(profileHeader);
        animateIn(profileHeader, animDelay++);

        int streak = p.has("streak") ? p.get("streak").getAsInt() : 0;
        View streakV = streakCard(streak, "连续记录");
        contentBox.addView(streakV);
        animateIn(streakV, animDelay++);

        int total = p.has("total_entries") ? p.get("total_entries").getAsInt() : 0;
        String joined = "";
        if (p.has("date_joined") && !p.get("date_joined").isJsonNull()) {
            String dj = p.get("date_joined").getAsString();
            if (dj.length() >= 10) joined = dj.substring(0, 10);
        }
        TextView stat = text("共记录 " + total + " 条心情" + (joined.isEmpty() ? "" : " · " + joined + " 加入"),
                Theme.INK_SOFT, 13);
        stat.setPadding(0, dp(6), 0, dp(10));
        contentBox.addView(stat);

        TextView badgeTitle = text("徽章墙", Theme.INK, 16, true);
        badgeTitle.setPadding(0, dp(4), 0, dp(8));
        contentBox.addView(badgeTitle);

        LinearLayout badgeGrid = new LinearLayout(requireContext());
        badgeGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int perRow = 3, i = 0;
        if (p.has("badges")) {
            for (JsonElement el : p.getAsJsonArray("badges")) {
                JsonObject b = el.getAsJsonObject();
                if (i % perRow == 0) {
                    row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setLayoutParams(lp(lpM(), lpW(), 0, 0, 0, dp(10)));
                    badgeGrid.addView(row);
                }
                row.addView(badgeCell(b.get("emoji").getAsString(),
                        b.get("name").getAsString(), b.get("days").getAsInt()));
                i++;
            }
        }
        if (i == 0) {
            badgeGrid.addView(text("还没有徽章，从连续记录 3 天开始收集吧", Theme.INK_SOFT, 13));
        }
        contentBox.addView(badgeGrid);
        animateIn(badgeGrid, animDelay++);

        View settings = buildSettings(false);
        contentBox.addView(settings);
        animateIn(settings, animDelay);
    }

    /** 连胜大卡：🔥 + N 天 + 说明 */
    private View streakCard(int streak, String caption) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView fire = text("🔥", Theme.INK, 40);
        fire.setPadding(0, 0, dp(16), 0);
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView days = text(streak + " 天", Theme.INK, 32, true);
        TextView cap = text(caption, Theme.INK_SOFT, 13);
        col.addView(days);
        col.addView(cap);
        card.addView(fire);
        card.addView(col);
        return card;
    }

    /** 一个徽章格 — 圆角卡片风格 */
    private View badgeCell(String emoji, String name, int days) {
        LinearLayout b = new LinearLayout(requireContext());
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        int pad = dp(14);
        b.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, lpW(), 1f);
        p.leftMargin = p.rightMargin = dp(4);
        b.setLayoutParams(p);

        // 圆角背景
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Theme.CARD);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Theme.DIVIDER);
        b.setBackground(gd);

        b.addView(text(emoji, Theme.INK, 28));
        TextView nameTv = text(name, Theme.INK, 13);
        nameTv.setPadding(0, dp(2), 0, 0);
        b.addView(nameTv);
        b.addView(text("连续 " + days + " 天", Theme.INK_SOFT, 11));
        return b;
    }

    // ---------- 设置区 ----------

    private View buildSettings(boolean guest) {
        LinearLayout box = card();
        box.addView(text("设置", Theme.INK, 16, true));

        // 主题预设
        box.addView(label("主题"));
        LinearLayout presetRow = new LinearLayout(requireContext());
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setLayoutParams(lp(lpM(), lpW(), 0, dp(6), 0, dp(10)));
        for (String[] preset : Theme.PRESETS) {
            String id = preset[0], name = preset[1], preview = preset[2];
            Button b = new Button(requireContext());
            boolean active = id.equals(app().config().themeId());
            b.setText("● " + name);
            b.setTextColor(active ? Theme.ACCENT : Theme.INK);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setMinWidth(0); b.setMinimumWidth(0);
            b.setMinHeight(0); b.setMinimumHeight(0);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setOnClickListener(v -> applyTheme(id, app().config().accent()));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(lpW(), lpW());
            pp.rightMargin = dp(8);
            b.setLayoutParams(pp);
            presetRow.addView(b);
        }
        box.addView(presetRow);

        // 主题调整入口（替代旧版强调色取色器）
        box.addView(label("主题调整"));
        Button themeEditorBtn = ghostBtn("自定义背景、卡片、强调色 →");
        themeEditorBtn.setOnClickListener(v -> showThemeEditor());
        box.addView(themeEditorBtn);

        // 服务器地址
        box.addView(label("服务器地址"));
        LinearLayout serverRow = new LinearLayout(requireContext());
        serverRow.setOrientation(LinearLayout.HORIZONTAL);
        serverRow.setGravity(Gravity.CENTER_VERTICAL);
        serverRow.setLayoutParams(lp(lpM(), lpW(), 0, dp(6), 0, dp(6)));
        EditText etServer = new EditText(requireContext());
        etServer.setText(app().config().serverBase());
        etServer.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        etServer.setBackground(Theme.createInputBg());
        etServer.setTextColor(Theme.INK);
        etServer.setHintTextColor(Theme.INK_SOFT);
        etServer.setSingleLine(true);
        etServer.setTextSize(13);
        etServer.setPadding(dp(10), dp(8), dp(10), dp(8));
        etServer.setLayoutParams(new LinearLayout.LayoutParams(0, lpW(), 1f));
        Button saveServer = primaryBtn("保存");
        saveServer.setOnClickListener(v -> {
            app().config().setServerBase(etServer.getText().toString());
            Toast.makeText(getContext(), "已保存，重启后完全生效", Toast.LENGTH_SHORT).show();
        });
        serverRow.addView(etServer);
        serverRow.addView(saveServer);
        box.addView(serverRow);

        // 刷新推荐目录
        Button refreshCatalog = ghostBtn("刷新推荐内容缓存");
        TextView catState = text("", Theme.INK_SOFT, 12);
        refreshCatalog.setOnClickListener(v -> {
            refreshCatalog.setEnabled(false);
            catState.setText("刷新中…");
            Bg.run(() -> new SyncEngine(app().config(), app().api(), app().db()).refreshCatalog(),
                    ok -> { refreshCatalog.setEnabled(true); catState.setText(ok ? "已更新（离线也能给推荐）" : "刷新失败，检查网络"); },
                    err -> { refreshCatalog.setEnabled(true); catState.setText("刷新失败：" + err.getMessage()); });
        });
        box.addView(refreshCatalog);
        box.addView(catState);

        if (!guest && app().config().loggedIn()) {
            // 退出登录
            Button logout = new Button(requireContext());
            logout.setText("退出登录");
            logout.setTextColor(Theme.DANGER);
            logout.setBackground(Theme.createOutlineButton());
            logout.setTextSize(14);
            logout.setPadding(dp(20), dp(12), dp(20), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(lpM(), lpW());
            lp.topMargin = dp(16);
            logout.setLayoutParams(lp);
            logout.setOnClickListener(v -> confirmLogout());
            box.addView(logout);
        }

        return box;
    }

    private void applyTheme(String id, String accent) {
        app().config().setThemeId(id);
        app().config().setAccent(accent);
        Theme.apply(id, null, null, accent);
        if (getActivity() != null) getActivity().recreate();   // 重建生效
    }

    private void applyTheme(String id, String bgHex, String cardHex, String accentHex) {
        app().config().setThemeId(id);
        app().config().setThemeBg(bgHex);
        app().config().setThemeCard(cardHex);
        app().config().setAccent(accentHex);
        Theme.apply(id, bgHex, cardHex, accentHex);
        if (getActivity() != null) getActivity().recreate();
    }

    // ========== 主题编辑器（3 色自定义） ==========

    /** 打开主题调整对话框：4 预设 + 3 色自定义（背景/卡片/强调色） */
    private void showThemeEditor() {
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        body.setPadding(pad, pad, pad, pad);

        // 1. 预设选择
        TextView tvPreset = new TextView(requireContext());
        tvPreset.setText("选择预设");
        tvPreset.setTextColor(Theme.INK);
        tvPreset.setTextSize(16);
        tvPreset.setTypeface(null, Typeface.BOLD);
        body.addView(tvPreset);

        LinearLayout presetRow = new LinearLayout(requireContext());
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setLayoutParams(lp(lpM(), lpW(), 0, dp(8), 0, dp(12)));
        String curId = app().config().themeId();
        for (String[] preset : Theme.PRESETS) {
            String id = preset[0], name = preset[1];
            Button b = new Button(requireContext());
            b.setText("● " + name);
            b.setTextColor(id.equals(curId) ? Theme.ACCENT : Theme.INK);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setMinWidth(0); b.setMinimumWidth(0);
            b.setMinHeight(0); b.setMinimumHeight(0);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setOnClickListener(v -> {
                // 应用预设但不关闭，让用户再微调
                app().config().setThemeId(id);
                // 清掉自定义颜色，回到预设
                app().config().setThemeBg("");
                app().config().setThemeCard("");
                app().config().setAccent("");
                Theme.applyPreset(id);
                if (getActivity() != null) getActivity().recreate();
            });
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(lpW(), lpW());
            pp.rightMargin = dp(8);
            b.setLayoutParams(pp);
            presetRow.addView(b);
        }
        body.addView(presetRow);

        // 2. 三色编辑区
        body.addView(colorEditRow("🎨 背景色", app().config().themeBg(),
                Theme.presetBg, Theme.BG, hex -> {
                    app().config().setThemeBg(hex);
                    Theme.apply(app().config().themeId(), hex, app().config().themeCard(), app().config().accent());
                    if (getActivity() != null) getActivity().recreate();
                }));
        body.addView(colorEditRow("📦 卡片色", app().config().themeCard(),
                Theme.presetCard, Theme.CARD, hex -> {
                    app().config().setThemeCard(hex);
                    Theme.apply(app().config().themeId(), app().config().themeBg(), hex, app().config().accent());
                    if (getActivity() != null) getActivity().recreate();
                }));
        body.addView(colorEditRow("🔘 强调色", app().config().accent(),
                Theme.presetAccent, Theme.ACCENT, hex -> {
                    app().config().setAccent(hex);
                    Theme.apply(app().config().themeId(), app().config().themeBg(), app().config().themeCard(), hex);
                    if (getActivity() != null) getActivity().recreate();
                }));

        new AlertDialog.Builder(requireContext())
                .setTitle("主题调整")
                .setView(body)
                .setPositiveButton("完成", null)
                .show();
    }

    /** 一行颜色编辑区：标签 + 9 色板 + 自定义按钮 */
    private LinearLayout colorEditRow(String label, String curHex, String presetHex, int currentColor,
                                      Consumer<String> onApply) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(lp(lpM(), lpW(), 0, dp(6), 0, dp(6)));

        // 标签：当前色预览圆点 + 名称 + HEX
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(lp(lpM(), lpW(), 0, 0, 0, dp(4)));

        View dot = new View(requireContext());
        int dotSz = dp(16);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSz, dotSz);
        dotLp.rightMargin = dp(8);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotGd = new GradientDrawable();
        dotGd.setShape(GradientDrawable.OVAL);
        dotGd.setColor(currentColor);
        dot.setBackground(dotGd);
        header.addView(dot);

        String hex = (curHex != null && !curHex.isEmpty()) ? curHex : presetHex;
        TextView tv = new TextView(requireContext());
        tv.setText(label + "  " + hex);
        tv.setTextColor(Theme.INK);
        tv.setTextSize(13);
        header.addView(tv);
        row.addView(header);

        // 色板：预设色 + 常见色
        LinearLayout swatches = new LinearLayout(requireContext());
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setLayoutParams(lp(lpM(), lpW(), 0, 0, 0, dp(4)));

        // 收集候选色：当前预设色 + 各预设的对应色 + 常用色
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
        // 当前预设色
        candidates.add(presetHex);
        // 所有预设的对应色
        for (String[] p : Theme.PRESETS) {
            String hex2;
            if (label.contains("背景")) hex2 = p[3];
            else if (label.contains("卡片")) hex2 = p[4];
            else hex2 = p[5];
            candidates.add(hex2);
        }
        // 常用色
        String[] extras = label.contains("强调")
                ? new String[]{"#7d9b76", "#5ea07c", "#d18a9a", "#7FA6E8", "#FF9F68", "#9BD1C6", "#F7A6C4", "#E8736B", "#8E94B8"}
                : new String[]{"#f6f1e7", "#26241f", "#eef6f1", "#faf0f2", "#ffffff", "#f0f0f0", "#333333", "#2c2c2c", "#ece7db"};
        for (String e : extras) candidates.add(e);

        for (String hex2 : candidates) {
            View swatch = new View(requireContext());
            int sz = dp(28);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(sz, sz);
            slp.rightMargin = dp(6);
            swatch.setLayoutParams(slp);
            GradientDrawable sgd = new GradientDrawable();
            sgd.setShape(GradientDrawable.OVAL);
            sgd.setColor(parseColor(hex2));
            String fHex = hex2;
            // 边框：选中色高亮
            boolean isSelected = hex.equalsIgnoreCase(hex2);
            sgd.setStroke(dp(2), isSelected ? Theme.INK : 0x22000000);
            swatch.setBackground(sgd);
            swatch.setOnClickListener(v -> onApply.accept(fHex));
            swatches.addView(swatch);
        }
        row.addView(swatches);

        // 自定义按钮
        Button customBtn = ghostBtn("自定义…");
        customBtn.setOnClickListener(v -> showColorPickerFor(label, curHex, presetHex, onApply));
        row.addView(customBtn);

        // 恢复默认
        Button resetBtn = ghostBtn("恢复预设");
        resetBtn.setOnClickListener(v -> onApply.accept(""));
        row.addView(resetBtn);

        return row;
    }

    /** 打开 HSV 取色器对话框 */
    private void showColorPickerFor(String label, String curHex, String presetHex, Consumer<String> onApply) {
        ColorPickerView picker = new ColorPickerView(requireContext());
        String initial = (curHex != null && !curHex.isEmpty()) ? curHex : presetHex;
        picker.setColor(initial);
        picker.setOnColorChangeListener((color, hex) -> { /* 实时预览由 picker 自身处理 */ });
        int pad = dp(20);
        picker.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(requireContext())
                .setTitle("选择" + label)
                .setView(picker)
                .setPositiveButton("确定", (d, w) -> onApply.accept(picker.getHex()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void askCustomAccent() {
        final EditText et = new EditText(requireContext());
        et.setHint("#RRGGBB，如 #7d9b76");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        String cur = app().config().accent();
        if (!cur.isEmpty()) et.setText(cur);
        new AlertDialog.Builder(requireContext())
                .setTitle("自定义强调色")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String hex = et.getText().toString().trim();
                    if (hex.matches("^#[0-9A-Fa-f]{6}$")) {
                        applyTheme(app().config().themeId(), hex);
                    } else {
                        Toast.makeText(getContext(), "格式不对，要 #RRGGBB", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录？")
                .setMessage("退出后本地记录保留，重新登录会继续同步。")
                .setPositiveButton("退出", (d, w) -> {
                    Bg.run(() -> { try { app().api().logout(); } catch (Exception ignored) { } return null; },
                            ok -> doLogout(),
                            err -> doLogout());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doLogout() {
        app().config().setToken("");
        app().config().setGuestMode(false);
        // 清同步游标涉及 Room 写库，挪后台线程，避免主线程查库闪退
        Bg.run(() -> app().db().kvDao().set("last_sync", ""));
        Intent i = new Intent(getActivity(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    /** 头像 + 用户名卡片，头像从服务器 avatar_url 加载 */
    private View createProfileHeader(JsonObject p) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        // 头像
        ImageView avatar = new ImageView(requireContext());
        int avatarSize = dp(56);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarLp.rightMargin = dp(16);
        avatar.setLayoutParams(avatarLp);

        // 圆形裁剪轮廓
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(Theme.CARD);
        avatarBg.setStroke(dp(2), Theme.DIVIDER);
        avatar.setBackground(avatarBg);
        avatar.setClipToOutline(true);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // 尝试加载头像
        String avatarUrl = p.has("avatar_url") && !p.get("avatar_url").isJsonNull()
                ? p.get("avatar_url").getAsString() : null;
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            loadAvatar(avatar, avatarUrl);
        } else {
            // 无头像时显示用户名首字背景
            avatar.setVisibility(View.GONE);
        }

        // 用户名 + 签名
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);

        String username = p.has("username") ? p.get("username").getAsString() : "用户";
        TextView name = text(username, Theme.INK, 18, true);
        col.addView(name);

        String bio = p.has("bio") && !p.get("bio").isJsonNull()
                ? p.get("bio").getAsString() : "";
        if (!bio.isEmpty()) {
            TextView bioTv = text(bio, Theme.INK_SOFT, 13);
            bioTv.setPadding(0, dp(4), 0, 0);
            col.addView(bioTv);
        }

        card.addView(avatar);
        card.addView(col);
        return card;
    }

    /** 后台线程下载头像 bitmap，回到主线程设给 ImageView */
    private void loadAvatar(ImageView iv, String url) {
        Bg.run(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoInput(true);
                conn.connect();
                return BitmapFactory.decodeStream(conn.getInputStream());
            } catch (Exception e) {
                return null;
            }
        },
        bitmap -> {
            if (bitmap != null) {
                iv.setImageBitmap(bitmap);
            }
        },
        err -> { /* 头像加载失败，静默 */ });
    }

    /** 卡片淡入 + 上移动画 */
    private void animateIn(View v, int delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(20));
        v.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(300)
                .setStartDelay(delay * 120)
                .start();
    }

    // ---------- 视图小工具 ----------

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.createCardBg(getResources().getDisplayMetrics().density, 14));
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        box.setLayoutParams(lp(lpM(), lpW(), 0, 0, 0, dp(10)));
        return box;
    }

    private TextView label(String s) {
        TextView t = text(s, Theme.INK_SOFT, 13);
        t.setLayoutParams(lp(lpM(), lpW(), 0, dp(10), 0, dp(4)));
        return t;
    }

    private TextView text(String s, int color, float sizeSp) {
        return text(s, color, sizeSp, false);
    }

    private TextView text(String s, int color, float sizeSp, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private Button primaryBtn(String text) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setBackground(Theme.createPrimaryButton());
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        b.setLayoutParams(lp(lpM(), lpW()));
        return b;
    }

    private Button ghostBtn(String text) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setTextColor(Theme.ACCENT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(lpW(), lpW());
        p.leftMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(left, top, right, bottom);
        return p;
    }
    private static int lpM() { return LinearLayout.LayoutParams.MATCH_PARENT; }
    private static int lpW() { return LinearLayout.LayoutParams.WRAP_CONTENT; }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int parseColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }
}