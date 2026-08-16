package com.moodtree.app;

import android.app.Application;

import com.moodtree.app.db.AppDatabase;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.ApiClient;
import com.moodtree.app.util.Config;
import com.moodtree.app.util.ImageLoader;

/** 全局 Application：持有 Config / ApiClient / Room 数据库，启动时应用保存的主题。
 *  心情定义缓存加载、PNG 预下载都交给 SplashActivity 的初始化任务，避免重复。 */
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

        // 启动时应用保存的主题（纯内存静态字段，无数据库访问，主线程安全）
        // 使用 3 色自定义：bg/card/accent，空串 = 用预设值
        Theme.apply(config.themeId(), config.themeBg(), config.themeCard(), config.themeAccent());

        // 初始化图片磁盘缓存目录（实际下载交给 SplashActivity，保证进主界面即用）
        ImageLoader.init(this);
    }

    public Config config() { return config; }
    public ApiClient api() { return api; }
    public AppDatabase db() { return db; }
}