package com.moodtree.app;

import android.app.Application;

import com.moodtree.app.db.AppDatabase;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.ApiClient;
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

        // 离线兜底：上次缓存的心情定义（没缓存就用代码里的默认 10 种）
        String cached = db.kvDao().get("moods_cache");
        if (cached != null) MoodMeta.overrideFromCatalogJson(cached);

        // 启动时应用保存的主题
        Theme.apply(config.themeId(), config.accent());
    }

    public Config config() { return config; }
    public ApiClient api() { return api; }
    public AppDatabase db() { return db; }
}
