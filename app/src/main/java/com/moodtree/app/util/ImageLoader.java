package com.moodtree.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/** 后台加载网络图片，设到 ImageView（主线程回调）。复用 Bg 线程池。
 *  与 MeFragment.loadAvatar 同模式，无第三方图片库依赖。
 *  内置按 URL 的 Bitmap 缓存（最大 4MB），同一心情图片多处复用时不重复下载。 */
public class ImageLoader {

    private static final int MAX_CACHE_BYTES = 4 * 1024 * 1024; // 4MB
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    /** 加载一张图片，设到 ImageView（主线程回调）。出错时静默——ImageView 保持原样（透明/占位）。 */
    public static void load(ImageView iv, String url) {
        // 先查缓存
        Bitmap cached = cache.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        Bg.run(() -> {
            try {
                URL u = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
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
                cache.put(url, bitmap);
                iv.setImageBitmap(bitmap);
            }
        },
        err -> { /* 静默 */ });
    }

    /** 从缓存取 Bitmap（没有返回 null，不触发网络请求） */
    public static Bitmap getCached(String url) {
        return cache.get(url);
    }

    /** 加载 Bitmap（异步），主线程回调。缓存命中时同步回调。 */
    public static void loadBitmap(String url, Consumer<Bitmap> callback) {
        Bitmap cached = cache.get(url);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        Bg.run(() -> {
            try {
                URL u = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
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
                cache.put(url, bitmap);
                callback.accept(bitmap);
            }
        },
        err -> { /* 静默 */ });
    }
}