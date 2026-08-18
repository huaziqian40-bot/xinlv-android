package com.moodtree.app.ui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moodtree.app.App;
import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.sync.SyncEngine;
import com.moodtree.app.util.Bg;

/** 主界面：底部导航四页（日历/推荐/树洞/我的）。游客与登录用户共用，各页内部按状态适配。
 *  本类还承担本地写库 + 触发同步的入口（saveMoodEntry / deleteMoodEntry / requestSync）。
 *  含情绪视觉影响：最新心情叠色 + 雨滴动画（难过/孤独/麻木）。 */
public class MainActivity extends AppCompatActivity {

    private CalendarFragment calendarFrag;
    private RecommendFragment recommendFrag;
    private ChatFragment chatFrag;
    private GameFragment gameFrag;
    private MeFragment meFrag;
    private Fragment active;

    // ---- AI 主动消息轮询 ----
    private static final String PROACTIVE_CHANNEL_ID = "proactive";
    private static final long PROACTIVE_POLL_MS = 60_000L;
    private final Handler proactiveHandler = new Handler(Looper.getMainLooper());
    private boolean proactivePolling;
    private final Runnable proactiveTick = new Runnable() {
        @Override public void run() {
            pollProactive();
            proactiveHandler.postDelayed(this, PROACTIVE_POLL_MS);
        }
    };

    // ---- 情绪视觉影响 ----
    private MoodOverlayView moodOverlay;
    private RainView rainContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 没登录也不是游客 → 回登录页（防御性，正常不会走到）
        if (!((App) getApplication()).config().canEnterMain()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        // 主题：根背景 + 底部导航栏背景
        findViewById(android.R.id.content).setBackgroundColor(Theme.BG);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setBackgroundColor(Theme.CARD);
        // 顶部避让状态栏（只给内容容器加顶部 padding，底部导航栏仍贴系统底）
        com.moodtree.app.util.Insets.applyTop(findViewById(R.id.fragmentContainer));

        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag("calendar") == null) {
            // 首次进入：一次性把四个 Fragment 都加进来，切换时只 show/hide（保留各自状态）
            calendarFrag = new CalendarFragment();
            recommendFrag = new RecommendFragment();
            chatFrag = new ChatFragment();
            gameFrag = new GameFragment();
            meFrag = new MeFragment();
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, meFrag, "me").hide(meFrag)
                    .add(R.id.fragmentContainer, gameFrag, "game").hide(gameFrag)
                    .add(R.id.fragmentContainer, chatFrag, "chat").hide(chatFrag)
                    .add(R.id.fragmentContainer, recommendFrag, "recommend").hide(recommendFrag)
                    .add(R.id.fragmentContainer, calendarFrag, "calendar")
                    .commit();
            active = calendarFrag;
        } else {
            // recreate（换主题/深浅色切换）后系统已自动恢复各 Fragment（含 show/hide 状态），
            // 找回引用即可，不能再 add——否则会叠一层重复页面
            calendarFrag = (CalendarFragment) fm.findFragmentByTag("calendar");
            recommendFrag = (RecommendFragment) fm.findFragmentByTag("recommend");
            chatFrag = (ChatFragment) fm.findFragmentByTag("chat");
            gameFrag = (GameFragment) fm.findFragmentByTag("game");
            meFrag = (MeFragment) fm.findFragmentByTag("me");
            active = calendarFrag;
            for (Fragment f : new Fragment[]{recommendFrag, chatFrag, gameFrag, meFrag}) {
                if (f != null && !f.isHidden()) { active = f; break; }
            }
        }

        // 页签顺序（用于确定切换方向）
        final int[] TAB_ORDER = {R.id.nav_calendar, R.id.nav_recommend, R.id.nav_chat, R.id.nav_game, R.id.nav_me};
        nav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();
            if (id == R.id.nav_recommend) target = recommendFrag;
            else if (id == R.id.nav_chat) target = chatFrag;
            else if (id == R.id.nav_game) target = gameFrag;
            else if (id == R.id.nav_me) target = meFrag;
            else target = calendarFrag;
            if (target == active) return true;

            // 计算切换方向：右滑（新页在右边→新页从右滑入，旧页向左滑出）
            int oldIdx = -1, newIdx = -1;
            int curId = -1;
            if (active == calendarFrag) curId = R.id.nav_calendar;
            else if (active == recommendFrag) curId = R.id.nav_recommend;
            else if (active == chatFrag) curId = R.id.nav_chat;
            else if (active == gameFrag) curId = R.id.nav_game;
            else if (active == meFrag) curId = R.id.nav_me;
            for (int i = 0; i < TAB_ORDER.length; i++) {
                if (TAB_ORDER[i] == curId) oldIdx = i;
                if (TAB_ORDER[i] == id) newIdx = i;
            }
            boolean forward = newIdx > oldIdx;

            if (forward) {
                fm.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                        .hide(active).show(target).commit();
            } else {
                fm.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
                        .hide(active).show(target).commit();
            }
            active = target;
            // 切到某页时让它刷新数据（联网后可能已变化）
            if (target instanceof Refreshable) ((Refreshable) target).refresh();
            return true;
        });
        // 恢复当前页签：首次默认日历；recreate 时留在原页，不再跳回日历
        int tabId = R.id.nav_calendar;
        if (active == recommendFrag) tabId = R.id.nav_recommend;
        else if (active == chatFrag) tabId = R.id.nav_chat;
        else if (active == gameFrag) tabId = R.id.nav_game;
        else if (active == meFrag) tabId = R.id.nav_me;
        nav.setSelectedItemId(tabId);
        // recreate 恢复时，当前页的内存状态（已加载标记/消息列表）已随旧实例销毁，
        // 主动触发一次刷新，否则停留在该页会显示空白（首次进入时各页自己会加载，不用触发）
        if (savedInstanceState != null && active instanceof Refreshable) {
            nav.post(() -> ((Refreshable) active).refresh());
        }

        // 进主界面后异步同步一轮（登录用户）；游客静默跳过
        requestSync(null);

        // 创建通知渠道（Android 8+ 必需，重复创建安全）
        createNotificationChannel();
        // 在进入主界面时请求通知权限，不依赖登录状态；否则用户可能永远看不到权限弹窗
        requestNotificationPermission();
        // 开始 AI 主动消息轮询
        startProactivePolling();

        // ---- 情绪视觉影响 ----
        moodOverlay = findViewById(R.id.moodOverlay);
        rainContainer = findViewById(R.id.rainContainer);
        updateMoodVisual();
    }

    // ============ 情绪视觉影响 ============

    /** 那些触发下雨的心情 key（与 Windows 端 + 网页端一致） */
    private static final String[] RAIN_MOODS = {"sad", "lonely", "numb"};

    /** 从数据库取最新一条心情，更新叠色和雨滴效果 */
    public void updateMoodVisual() {
        Bg.run(() -> {
                    try {
                        MoodEntry latest = app().db().moodDao().getLatest();
                        if (latest == null) return null;
                        MoodMeta meta = MoodMeta.of(latest.mood);
                        return new Object[]{meta, latest.intensityLevel, latest.intensityPercent};
                    } catch (Exception e) {
                        return null;
                    }
                },
                data -> {
                    if (data == null) {
                        moodOverlay.clear();
                        moodOverlay.setVisibility(android.view.View.GONE);
                        rainContainer.stopRain();
                        return;
                    }
                    MoodMeta meta = (MoodMeta) data[0];
                    int intensityLevel = (int) data[1];
                    int intensityPercent = (int) data[2];

                    // 叠色：径向渐变，颜色来自心情色，强度缩放透明度
                    double opacity = 0.08;
                    if (intensityLevel >= 2) opacity = 0.12;
                    if (intensityLevel >= 3) opacity = 0.18;
                    if (intensityLevel >= 4) opacity = 0.25;
                    opacity *= (0.8 + 0.4 * intensityPercent / 100.0);

                    int color;
                    try {
                        color = android.graphics.Color.parseColor(meta.color);
                    } catch (Exception e) {
                        color = android.graphics.Color.GRAY;
                    }
                    moodOverlay.setMoodColor(color, (float) opacity);
                    moodOverlay.setVisibility(android.view.View.VISIBLE);

                    // 雨滴动画：难过/孤独/麻木 三种心情
                    boolean shouldRain = false;
                    for (String rm : RAIN_MOODS) {
                        if (rm.equals(meta.key)) { shouldRain = true; break; }
                    }
                    if (shouldRain && intensityLevel >= 1) {
                        rainContainer.startRain();
                    } else {
                        rainContainer.stopRain();
                    }
                },
                err -> { /* 静默 */ });
    }

    /** 保存（新建或编辑）一条心情记录到本地库，打 dirty 标记，再异步同步。
     *  onDone 在 DB 写完后在主线程回调（用于刷新 UI）。 */
    public void saveMoodEntry(MoodEntry e, Runnable onDone) {
        Bg.run(() -> {
            app().db().moodDao().upsert(e);
            Bg.ui(() -> {
                refreshCalendar();
                updateMoodVisual();
                if (onDone != null) onDone.run();
            });
            requestSync(null);
        });
    }

    /** 保存（无回调），兼容旧调用 */
    public void saveMoodEntry(MoodEntry e) {
        saveMoodEntry(e, null);
    }

    /** 删除一条记录（墓碑：deleted=1 + dirty=1），再异步同步。
     *  onDone 在 DB 写完后在主线程回调。 */
    public void deleteMoodEntry(MoodEntry e, Runnable onDone) {
        Bg.run(() -> {
            e.deleted = true;
            e.updatedAt = com.moodtree.app.util.Dates.nowIso();
            e.dirty = true;
            app().db().moodDao().upsert(e);
            Bg.ui(() -> {
                refreshCalendar();
                updateMoodVisual();
                if (onDone != null) onDone.run();
            });
            requestSync(null);
        });
    }

    /** 删除（无回调），兼容旧调用 */
    public void deleteMoodEntry(MoodEntry e) {
        deleteMoodEntry(e, null);
    }

    /** 触发一轮同步。游客/未登录跳过；结果可选回调到主线程。 */
    public void requestSync(SyncEngine.SyncResult.Callback cb) {
        App a = app();
        if (!a.config().loggedIn()) return;   // 游客不同步
        Bg.run(() -> {
            SyncEngine.SyncResult r = new SyncEngine(a.config(), a.api(), a.db()).sync();
            Bg.ui(() -> {
                refreshCalendar();
                updateMoodVisual();
                if (cb != null) cb.onResult(r);
            });
        });
    }

    /** 让日历页重新读库刷新（写库/同步后调用） */
    private void refreshCalendar() {
        if (calendarFrag.isAdded()) calendarFrag.reload();
    }

    /** 给 Fragment 拿 App 的便捷方法 */
    public App app() { return (App) getApplication(); }

    /** 切换到推荐页并自动选中某心情（记心情后调用） */
    public void switchToRecommend(String mood) {
        if (recommendFrag == null) return;
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_recommend);
        // 模拟点击推荐页签
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (active != recommendFrag) {
            fm.beginTransaction().hide(active).show(recommendFrag).commit();
            active = recommendFrag;
        }
        // 延迟一点等页面切换完成再触发 select
        findViewById(android.R.id.content).post(() -> recommendFrag.select(mood));
    }

    // ============ AI 主动消息轮询 + 系统通知 ============

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    PROACTIVE_CHANNEL_ID, "树洞来信", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("AI 树洞主动发来的消息提醒");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** 主动消息轮询：后台请求 /chat/proactive/，当前在聊天页就直接追加气泡，否则弹系统通知。
     *  与网络同步独立，登录后定时触发；游客不轮询。 */
    private void pollProactive() {
        App a = app();
        if (!a.config().loggedIn()) return;
        String since = a.config().lastProactiveCheck();
        Bg.run(() -> {
                    if (!a.api().ping()) return null;      // 离线静默跳过
                    return a.api().chatProactive(since.isEmpty() ? null : since);
                },
                resp -> {
                    if (resp == null) return;
                    String serverTime = "";
                    if (resp.has("server_time") && !resp.get("server_time").isJsonNull()) {
                        serverTime = resp.get("server_time").getAsString();
                    }
                    if (resp.has("messages") && resp.get("messages").isJsonArray()) {
                        for (JsonElement el : resp.getAsJsonArray("messages")) {
                            JsonObject m = el.getAsJsonObject();
                            String content = m.has("content") ? m.get("content").getAsString() : "";
                            if (content.isEmpty()) continue;
                            if (active == chatFrag && !chatFrag.isHidden()) {
                                chatFrag.addProactiveMessage(content);
                            } else {
                                showProactiveNotification(content);
                            }
                        }
                    }
                    // 保存游标，下次只拉取更新的消息
                    if (!serverTime.isEmpty()) a.config().setLastProactiveCheck(serverTime);
                },
                err -> { /* 网络/鉴权失败静默，下轮再试 */ });
    }

    private void startProactivePolling() {
        if (proactivePolling) return;
        // 游客没有服务器会话，轮询没有意义
        if (!app().config().loggedIn()) return;
        proactivePolling = true;
        // 进入主界面立即检查一次，之后按固定间隔轮询
        proactiveHandler.post(proactiveTick);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Android 会根据用户上次选择决定是否再次显示确认框；不要用
            // shouldShowRequestPermissionRationale() 把请求提前拦截掉。
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void stopProactivePolling() {
        proactiveHandler.removeCallbacks(proactiveTick);
        proactivePolling = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 权限结果不阻塞功能，静默处理即可
    }

    /** 系统通知：类似微信消息提醒（点按打开应用，不做深链跳转） */
    private void showProactiveNotification(String text) {
        String title = "🌳 树洞来信";
        String body = text.length() > 120 ? text.substring(0, 120) + "…" : text;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, PROACTIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(this).notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (SecurityException e) {
            // 未授予通知权限：静默
        }
    }

    @Override
    protected void onDestroy() {
        stopProactivePolling();
        super.onDestroy();
    }
}
