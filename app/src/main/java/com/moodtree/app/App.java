package com.moodtree.app;

import android.app.Application;

import com.moodtree.app.db.AppDatabase;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.ApiClient;
import com.moodtree.app.util.Bg;
import com.moodtree.app.util.Config;

/** 全局 Application：持有 Config / ApiClient / Room 数据库，启动时应用保存的主题、加载缓存的心情定义。 */
public class App extends Application {

    private Config config;
    private ApiClient api;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        config = new Config(this);
        api = new ApiClient(config);
        db = AppDatabase.get(this);

        // 离线兜底：上次缓存的心情定义（没缓存就用代码里的默认 10 种）。
        // Room 禁止主线程查库，放后台线程；不阻塞启动（首次没缓存就用默认值）。
        Bg.run(() -> {
            String cached = db.kvDao().get("moods_cache");
            if (cached != null) MoodMeta.overrideFromCatalogJson(cached);
        });

        // 启动时应用保存的主题（纯内存静态字段，无数据库访问，主线程安全）
        // 使用 3 色自定义：bg/card/accent，空串 = 用预设值
        Theme.apply(config.themeId(), config.themeBg(), config.themeCard(), config.themeAccent());
    }

    public Config config() { return config; }
    public ApiClient api() { return api; }
    public AppDatabase db() { return db; }
}
