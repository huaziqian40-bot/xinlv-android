package com.moodtree.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moodtree.app.R;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Bg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 今日推荐：选心情 → 音乐/小行动/心理小知识/视频。
 *  在线调 /api/v1/recommend/；离线用登录时缓存的目录自行筛选（音乐仅在线可播）。
 *  行为对齐 Windows RecommendView：相同筛选规则、相同卡片分区。 */
public class RecommendFragment extends BaseFragment implements Refreshable {

    private ChipGroup chipMoods;
    private TextView tvState;
    private LinearLayout resultBox;
    private String selectedMood;

    private MediaPlayer player;       // 同时只放一首
    private Button playingBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_recommend, container, false);
        themeBackground(root);

        chipMoods = root.findViewById(R.id.chipMoods);
        tvState = root.findViewById(R.id.tvState);
        resultBox = root.findViewById(R.id.resultBox);

        for (MoodMeta m : MoodMeta.all()) {
            Chip chip = new Chip(requireContext());
            chip.setText(m.emoji + " " + m.label);
            chip.setCheckable(true);
            int color = parseColor(m.color);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));
            chip.setTextColor(darkerText(color));
            chip.setOnClickListener(v -> select(m.key));
            chipMoods.addView(chip);
        }
        return root;
    }

    @Override
    public void refresh() {
        // 目录刷新后心情定义可能变；简单起见不重建 chip（下次进页面会重建）
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPlayer();
    }

    private void select(String mood) {
        selectedMood = mood;
        stopPlayer();
        MoodMeta m = MoodMeta.of(mood);
        tvState.setText("正在为你准备「" + m.label + "」的推荐…");
        resultBox.removeAllViews();

        Bg.run(() -> {
                    JsonObject rec;
                    if (app().config().loggedIn() && app().api().ping()) {
                        rec = app().api().recommend(mood);          // 在线
                    } else {
                        rec = offlineRecommend(mood);                // 离线缓存
                    }
                    return rec;
                },
                this::render,
                err -> tvState.setText("推荐加载失败：" + err.getMessage()));
    }

    /** 离线：从目录缓存按心情筛选随机挑几条（规则向服务端看齐） */
    private JsonObject offlineRecommend(String mood) {
        JsonObject rec = new JsonObject();
        rec.addProperty("mood", mood);
        rec.addProperty("offline", true);

        JsonArray songs = new JsonArray();
        JsonArray activities = new JsonArray();
        JsonArray tips = new JsonArray();
        JsonObject video = null;

        List<JsonObject> pool = new ArrayList<>();
        for (String payload : app().db().catalogDao().all("songs")) {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            if (hasMood(o, mood)) pool.add(o);
        }
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(3, pool.size()); i++) songs.add(pool.get(i));

        pool.clear();
        for (String payload : app().db().catalogDao().all("activities")) {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            if (hasMood(o, mood)) pool.add(o);
        }
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(3, pool.size()); i++)
            activities.add(pool.get(i).get("text"));

        pool.clear();
        for (String payload : app().db().catalogDao().all("tips")) {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            if (!o.has("moods") || hasMood(o, mood)) pool.add(o);
        }
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(2, pool.size()); i++) tips.add(pool.get(i));

        for (String payload : app().db().catalogDao().all("videos")) {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            if (hasMood(o, mood)) { video = o; break; }
        }

        rec.add("songs", songs);
        rec.add("activities", activities);
        rec.add("tips", tips);
        if (video != null) rec.add("video", video);
        return rec;
    }

    private boolean hasMood(JsonObject o, String mood) {
        if (!o.has("moods") || !o.get("moods").isJsonArray()) return false;
        for (JsonElement el : o.getAsJsonArray("moods")) {
            if (mood.equals(el.getAsString())) return true;
        }
        return false;
    }

    /** 渲染推荐结果（主线程） */
    private void render(JsonObject rec) {
        if (!isAdded()) return;
        MoodMeta m = MoodMeta.of(rec.get("mood").getAsString());
        boolean offline = rec.has("offline") && rec.get("offline").getAsBoolean();
        resultBox.removeAllViews();

        JsonArray songs = rec.has("songs") ? rec.getAsJsonArray("songs") : null;
        JsonArray acts = rec.has("activities") ? rec.getAsJsonArray("activities") : null;
        JsonArray tips = rec.has("tips") ? rec.getAsJsonArray("tips") : null;
        boolean hasVideo = rec.has("video") && rec.get("video").isJsonObject();
        boolean any = (songs != null && songs.size() > 0) || (acts != null && acts.size() > 0)
                || (tips != null && tips.size() > 0) || hasVideo;

        if (!any) {
            tvState.setText(offline
                    ? "本地还没有推荐内容缓存：联网登录一次后，离线也能用推荐"
                    : "这份心情暂时没有推荐内容");
            return;
        }
        tvState.setText("给「" + m.label + "」的你" + (offline ? "（离线缓存内容）" : ""));

        if (songs != null) {
            LinearLayout card = card("🎵 听点音乐");
            for (JsonElement el : songs) {
                JsonObject s = el.getAsJsonObject();
                String text = s.get("title").getAsString() + " - " + s.get("artist").getAsString();
                String url = s.has("url") && !s.get("url").isJsonNull() ? s.get("url").getAsString() : "";
                Button play = ghostBtn("▶");
                TextView name = new TextView(requireContext());
                name.setText(text);
                name.setTextColor(Theme.INK);
                name.setTextSize(14);
                LinearLayout row = row(play, name);
                if (url.isEmpty() || offline) {
                    play.setEnabled(false);
                } else {
                    play.setOnClickListener(v -> togglePlay(url, play));
                }
                card.addView(row);
            }
            resultBox.addView(card);
        }

        if (acts != null) {
            LinearLayout card = card("🌱 可以试试这些小事");
            for (JsonElement el : acts) {
                TextView t = new TextView(requireContext());
                t.setText("· " + el.getAsString());
                t.setTextColor(Theme.INK);
                t.setTextSize(14);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                p.bottomMargin = dp(6);
                t.setLayoutParams(p);
                card.addView(t);
            }
            resultBox.addView(card);
        }

        if (tips != null) {
            LinearLayout card = card("💡 心理小知识");
            for (JsonElement el : tips) {
                JsonObject t = el.getAsJsonObject();
                TextView h = new TextView(requireContext());
                h.setText(t.get("title").getAsString());
                h.setTextColor(Theme.INK);
                h.setTextSize(14);
                h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
                TextView c = new TextView(requireContext());
                c.setText(t.get("content").getAsString());
                c.setTextColor(Theme.INK);
                c.setTextSize(13);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                p.topMargin = dp(2);
                p.bottomMargin = dp(8);
                c.setLayoutParams(p);
                card.addView(h);
                card.addView(c);
                if (t.has("source") && !t.get("source").getAsString().isEmpty()) {
                    TextView src = new TextView(requireContext());
                    src.setText("出处：" + t.get("source").getAsString());
                    src.setTextColor(Theme.INK_SOFT);
                    src.setTextSize(12);
                    card.addView(src);
                }
            }
            resultBox.addView(card);
        }

        if (hasVideo) {
            JsonObject v = rec.getAsJsonObject("video");
            LinearLayout card = card("🎬 看个视频");
            Button link = ghostBtn(v.get("title").getAsString() + "（浏览器打开）");
            link.setOnClickListener(view -> openBrowser(v.get("url").getAsString()));
            card.addView(link);
            resultBox.addView(card);
        }
    }

    // ---------- 视图小工具 ----------

    /** 一张卡片：标题 + 内容容器，带主题背景与内边距 */
    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Theme.CARD);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        box.setLayoutParams(lp);

        TextView h = new TextView(requireContext());
        h.setText(title);
        h.setTextColor(Theme.INK);
        h.setTextSize(16);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(10);
        h.setLayoutParams(hp);
        box.addView(h);
        return box;
    }

    /** 一行：播放按钮 + 名称，水平排列 */
    private LinearLayout row(Button play, TextView name) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(6);
        row.setLayoutParams(rp);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        np.leftMargin = dp(8);
        name.setLayoutParams(np);
        row.addView(play);
        row.addView(name);
        return row;
    }

    /** 幽灵风格按钮（透明底，强调色文字） */
    private Button ghostBtn(String text) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setTextColor(Theme.ACCENT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------- 音乐播放（在线；同时一首） ----------

    private void togglePlay(String url, Button btn) {
        if (playingBtn == btn) { stopPlayer(); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build());
            player.setDataSource(url);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> stopPlayer());
            player.setOnErrorListener((mp, what, extra) -> { stopPlayer(); return true; });
            player.prepareAsync();
            playingBtn = btn;
            btn.setText("⏸");
        } catch (Exception e) {
            tvState.setText("播放失败：" + e.getMessage());
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (playingBtn != null) { playingBtn.setText("▶"); playingBtn = null; }
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(getContext(), "打不开浏览器，请手动访问：" + url, Toast.LENGTH_LONG).show();
        }
    }

    private static int parseColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }

    /** 深色心情底色用浅字，浅色底用深字 */
    private static int darkerText(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255;
        return lum > 0.6 ? Theme.INK : Color.WHITE;
    }
}
