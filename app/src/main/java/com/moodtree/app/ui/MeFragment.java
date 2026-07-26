package com.moodtree.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

/** 我的：连胜/徽章/总记录（在线 profile，离线用缓存；游客用本机统计）+ 设置区
 *  （主题预设 + 强调色取色 + 服务器地址 + 刷新目录 + 退出登录）。对齐 Windows MeView。 */
public class MeFragment extends BaseFragment implements Refreshable {

    private LinearLayout contentBox;
    private TextView tvState;
    private boolean loaded;

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
        if (p == null) {
            tvState.setText("离线且暂无缓存数据，联网后这里会显示你的连胜和徽章");
            tvState.setVisibility(View.VISIBLE);
            contentBox.addView(buildSettings(false));
            return;
        }
        tvState.setVisibility(View.GONE);

        int streak = p.has("streak") ? p.get("streak").getAsInt() : 0;
        contentBox.addView(streakCard(streak, "连续记录"));

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

        contentBox.addView(buildSettings(false));
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

    /** 一个徽章格 */
    private View badgeCell(String emoji, String name, int days) {
        LinearLayout b = new LinearLayout(requireContext());
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Theme.CARD);
        int pad = dp(14);
        b.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, lpW(), 1f);
        p.leftMargin = p.rightMargin = dp(4);
        b.setLayoutParams(p);
        b.addView(text(emoji, Theme.INK, 28));
        b.addView(text(name, Theme.INK, 13));
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
            // 文字前缀圆点 + 名称，圆点用预览色（名称用墨色或选中用强调色）
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

        // 强调色取色
        box.addView(label("强调色"));
        LinearLayout swatchRow = new LinearLayout(requireContext());
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setLayoutParams(lp(lpM(), lpW(), 0, dp(6), 0, dp(6)));
        for (String hex : ACCENT_SWATCHES) {
            View dot = new View(requireContext());
            int sz = dp(28);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(sz, sz);
            dotLp.rightMargin = dp(8);
            dot.setLayoutParams(dotLp);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(parseColor(hex));
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setStroke(dp(2), app().config().accent().equalsIgnoreCase(hex) ? Theme.INK : 0x33000000);
            dot.setBackground(gd);
            dot.setOnClickListener(v -> applyTheme(app().config().themeId(), hex));
            swatchRow.addView(dot);
        }
        Button custom = ghostBtn("自定义…");
        custom.setOnClickListener(v -> askCustomAccent());
        swatchRow.addView(custom);
        box.addView(swatchRow);

        Button resetAccent = ghostBtn("恢复默认强调色");
        resetAccent.setOnClickListener(v -> applyTheme(app().config().themeId(), ""));
        box.addView(resetAccent);

        // 服务器地址
        box.addView(label("服务器地址"));
        LinearLayout serverRow = new LinearLayout(requireContext());
        serverRow.setOrientation(LinearLayout.HORIZONTAL);
        serverRow.setGravity(Gravity.CENTER_VERTICAL);
        serverRow.setLayoutParams(lp(lpM(), lpW(), 0, dp(6), 0, dp(6)));
        EditText etServer = new EditText(requireContext());
        etServer.setText(app().config().serverBase());
        etServer.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        etServer.setBackgroundColor(Theme.CARD);
        etServer.setSingleLine(true);
        etServer.setTextSize(13);
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
            logout.setBackgroundColor(Color.TRANSPARENT);
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
        Theme.apply(id, accent);
        if (getActivity() != null) getActivity().recreate();   // 重建生效
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

    // ---------- 视图小工具 ----------

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Theme.CARD);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        box.setLayoutParams(lp(lpM(), lpW(), 0, 0, 0, dp(14)));
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
