package com.moodtree.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.moodtree.app.App;
import com.moodtree.app.R;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.sync.SyncEngine;
import com.moodtree.app.util.Bg;
import com.moodtree.app.util.Config;
import com.moodtree.app.util.ImageLoader;

/** 启动页：每次进入 App 都显示，主题色背景 + 透明心履 Logo（淡入动画）。
 *  利用这段时间做初始化，保证进入主界面时"零等待"：
 *    - 首次启动（PNG 未缓存到磁盘）：后台下载全部心情/徽章 PNG（ImageLoader.preloadAllSync）
 *    - 非首次启动：后台同步服务器数据（SyncEngine 推+拉）+ 刷新推荐目录 + 预载心情定义
 *    - 两种情况都至少展示最短时长（约 1 秒），避免一闪而过
 *  初始化完成后跳转：已登录/游客 -> 主界面；否则 -> 登录页。 */
public class SplashActivity extends AppCompatActivity {

    // 最短展示时长（毫秒）。避免图片/同步很快时启动页一闪而过。
    private static final long MIN_SHOW_MS = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        App app = (App) getApplication();

        // 主题色背景（与主界面一致），根容器 + 状态栏
        findViewById(android.R.id.content).setBackgroundColor(Theme.BG);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(Theme.BG);
            // 浅色主题用深色状态栏图标，深色主题用浅色图标
            getWindow().getDecorView().setSystemUiVisibility(
                    Theme.isDarkTheme() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // Logo 淡入（Scale 11 稍大后缩到正常，和主界面入场动画风格一致）
        ImageView logo = findViewById(R.id.ivLogo);
        logo.setAlpha(0f);
        logo.setScaleX(1.15f);
        logo.setScaleY(1.15f);
        logo.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(500)
                .start();

        final long start = SystemClock.elapsedRealtime();
        Bg.run(() -> {
            // ---- 提前预热运行时内存：加载缓存的心情定义 ----
            try {
                String cached = app.db().kvDao().get("moods_cache");
                if (cached != null) MoodMeta.overrideFromCatalogJson(cached);
            } catch (Exception ignored) { }

            boolean isFirstLaunch;
            int cacheCount;
            try {
                cacheCount = ImageLoader.diskCacheCount();
                isFirstLaunch = cacheCount < ImageLoader.PNG_COUNT;
            } catch (Exception e) {
                cacheCount = 0;
                isFirstLaunch = true;
            }
            if (isFirstLaunch) {
                // 首次启动：下载全部 PNG 到磁盘缓存，进入主界面后秒开不卡
                ImageLoader.preloadAllSync(app.config().serverBase());
            } else if (app.config().loggedIn()) {
                // 非首次 + 已登录：利用启动页时间同步一轮 + 刷新推荐目录
                try {
                    new SyncEngine(app.config(), app.api(), app.db()).refreshCatalog();
                    new SyncEngine(app.config(), app.api(), app.db()).sync();
                } catch (Exception ignored) { }
            }
            return null;
        },
        ok -> {
            // 初始化完成（无论成功与否），保证最短展示时长后进入下一步
            long elapsed = SystemClock.elapsedRealtime() - start;
            long delay = Math.max(0, MIN_SHOW_MS - elapsed);
            findViewById(android.R.id.content).postDelayed(() -> goNext(), delay);
        },
        err -> {
            // 初始化出错也不阻塞进入
            long elapsed = SystemClock.elapsedRealtime() - start;
            long delay = Math.max(0, MIN_SHOW_MS - elapsed);
            findViewById(android.R.id.content).postDelayed(() -> goNext(), delay);
        });
    }

    /** 跳转到登录页（未登录/非游客）或主界面（已登录/游客） */
    private void goNext() {
        Config config = ((App) getApplication()).config();
        Intent i = config.canEnterMain()
                ? new Intent(this, MainActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}