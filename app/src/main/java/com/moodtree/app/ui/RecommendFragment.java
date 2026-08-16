package com.moodtree.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import com.moodtree.app.util.Config;
import com.moodtree.app.util.ImageLoader;

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

        // 标题文字主题化
        root.<TextView>findViewById(R.id.tvRecTitle).setTextColor(Theme.INK);
        root.<TextView>findViewById(R.id.tvRecSub).setTextColor(Theme.INK_SOFT);

        boolean isDark = Theme.isDarkTheme();
        String serverBase = new Config(requireContext()).serverBase();
        for (MoodMeta m : MoodMeta.all()) {
            Chip chip = new Chip(requireContext());
            chip.setText(m.label);
            // 用 PNG 心情图作图标；未加载完成前先显示 emoji 兜底
            chip.setText(m.emoji + " " + m.label);
            ImageLoader.loadBitmap(serverBase + "/static/" + m.image, bmp -> {
                if (bmp != null && chip.isAttachedToWindow()) {
                    chip.setChipIcon(new BitmapDrawable(getResources(), bmp));
                    chip.setChipIconSize(dp(18));
                    chip.setChipIconVisible(true);
                    chip.setText(m.label);
                }
            });
            chip.setCheckable(true);
            int color = parseColor(m.color);
            // 提高饱和度
            color = Theme.adjustSaturation(color, 1.3f);
            int unselected, selected;
            if (isDark) {
                // 深色主题：用暗色底 + 心情色作为边框/强调
                unselected = darken(color, 0.75f);
                selected = darken(color, 0.50f);
            } else {
                // 浅色主题：用白色淡化心情色
                unselected = lighten(color, 0.85f);
                selected = lighten(color, 0.60f);
            }
            int[][] states = {{android.R.attr.state_checked}, {}};
            int[] colors = {selected, unselected};
            chip.setChipBackgroundColor(new android.content.res.ColorStateList(states, colors));
            chip.setTextColor(isDark ? lighterText(selected) : Theme.INK);
            chip.setChipStrokeWidth(0f);
            chip.setChipCornerRadius(dp(20));
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

    /** 选中某心情并加载推荐 */
    public void select(String mood) {
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

    /** 离线：从目录缓存按心情筛选随机挑几条（规则向服务端看齐：正面给小知识，负面/中性给小练习） */
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
        boolean positive = MoodMeta.isPositive(mood);
        for (int i = 0; i < Math.min(positive ? 2 : 3, pool.size()); i++)
            activities.add(pool.get(i).get("text"));

        if (positive) {
            // 服务端规则：心理小知识只推给正面心情
            pool.clear();
            for (String payload : app().db().catalogDao().all("tips")) {
                JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
                if (!o.has("moods") || hasMood(o, mood)) pool.add(o);
            }
            Collections.shuffle(pool);
            for (int i = 0; i < Math.min(2, pool.size()); i++) tips.add(pool.get(i));
        } else {
            // 负面/中性：即时小练习（兜底文案与服务端 QUICK_PRACTICE 一致）
            rec.addProperty("practice", quickPractice(mood));
        }

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

    /** 各负面/中性心情的即时小练习文案（与服务端 recommendations.QUICK_PRACTICE 一致） */
    private static String quickPractice(String mood) {
        switch (mood) {
            case "anxious": return "试试 4-7-8 呼吸：吸气 4 秒，屏息 7 秒，缓缓呼气 8 秒，重复 4 轮。";
            case "angry":   return "找个没人的地方，把想说的话写下来或大声说出来，先让情绪流动，再决定怎么做。";
            case "sad":     return "允许自己难过一会儿，给信任的人发条消息，哪怕只是说一句「我今天不太好」。";
            case "tired":   return "放下手机，闭眼休息 10 分钟，或者去窗边看看远处，让眼睛和大脑都松一下。";
            case "lonely":  return "给一个久未联系的人发条消息，或出门走到有人的地方，孤独常常因连接而缓解。";
            case "numb":    return "做一件具体的小事：喝口水、洗把脸、整理桌面，用身体的动作把自己拉回当下。";
            default:        return "";
        }
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
        String practice = rec.has("practice") && !rec.get("practice").isJsonNull()
                ? rec.get("practice").getAsString() : "";
        boolean hasVideo = rec.has("video") && rec.get("video").isJsonObject();
        boolean any = (songs != null && songs.size() > 0) || (acts != null && acts.size() > 0)
                || (tips != null && tips.size() > 0) || hasVideo || !practice.isEmpty();

        if (!any) {
            tvState.setText(offline
                    ? "本地还没有推荐内容缓存：联网登录一次后，离线也能用推荐"
                    : "这份心情暂时没有推荐内容");
            return;
        }
        tvState.setText("给「" + m.label + "」的你" + (offline ? "（离线缓存内容）" : ""));

        int delay = 0;
        if (songs != null && songs.size() > 0) {
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
            animateIn(card, delay++);
        }

        if (acts != null && acts.size() > 0) {
            LinearLayout card = card("🌱 可以试试这些小事");
            for (JsonElement el : acts) {
                TextView t = new TextView(requireContext());
                t.setText("· " + el.getAsString());
                t.setTextColor(Theme.INK);
                t.setTextSize(14);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                p.bottomMargin = dp(2);
                t.setLayoutParams(p);
                card.addView(t);
            }
            resultBox.addView(card);
            animateIn(card, delay++);
        }

        // 服务端规则：负面/中性心情没有小知识（tips 为空数组），空卡不能渲染，否则只见标题不见内容
        if (tips != null && tips.size() > 0) {
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
                p.bottomMargin = dp(4);
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
            animateIn(card, delay++);
        }

        // 即时小练习（负面/中性心情，服务端 practice 字段；与网页端结果页一致）
        if (!practice.isEmpty()) {
            LinearLayout card = card("🌬️ 来试一个小练习");
            TextView t = new TextView(requireContext());
            t.setText(practice);
            t.setTextColor(Theme.INK);
            t.setTextSize(14);
            card.addView(t);
            resultBox.addView(card);
            animateIn(card, delay++);
        }

        if (hasVideo) {
            JsonObject v = rec.getAsJsonObject("video");
            LinearLayout card = card("🎬 看个视频");
            Button link = ghostBtn(v.get("title").getAsString() + "（浏览器打开）");
            link.setOnClickListener(view -> openBrowser(v.get("url").getAsString()));
            card.addView(link);
            resultBox.addView(card);
            animateIn(card, delay++);
        }
    }

    /** 卡片淡入动画（带递增延迟，依次弹出） */
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

    /** 一张卡片：标题 + 内容容器，带 card_bg drawable 背景 */
    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.createCardBg(getResources().getDisplayMetrics().density, 14));
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        box.setLayoutParams(lp);

        TextView h = new TextView(requireContext());
        h.setText(title);
        h.setTextColor(Theme.INK);
        h.setTextSize(16);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(3);
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
        rp.bottomMargin = dp(1);
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
        b.setPadding(dp(8), dp(2), dp(8), dp(2));
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

    /** 将 color 与白色按比例混合，ratio 越大越接近白色 */
    private static int lighten(int color, float ratio) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int wr = 255, wg = 255, wb = 255;
        return Color.rgb(
                (int) (r * (1 - ratio) + wr * ratio),
                (int) (g * (1 - ratio) + wg * ratio),
                (int) (b * (1 - ratio) + wb * ratio));
    }

    /** 将 color 与黑色按比例混合，ratio 越大越接近黑色（深色主题用） */
    private static int darken(int color, float ratio) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb(
                (int) (r * (1 - ratio)),
                (int) (g * (1 - ratio)),
                (int) (b * (1 - ratio)));
    }

    /** 深色底用浅色文字，浅色底用深色文字 */
    private static int lighterText(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255;
        return lum > 0.5 ? Theme.INK : Color.WHITE;
    }

    /** 深色心情底色用浅字，浅色底用深字 */
    private static int darkerText(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255;
        return lum > 0.6 ? Theme.INK : Color.WHITE;
    }
}