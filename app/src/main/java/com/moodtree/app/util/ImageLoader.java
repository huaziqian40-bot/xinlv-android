package com.moodtree.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/** 后台加载网络图片，设到 ImageView（主线程回调）。复用 Bg 线程池。
 *  内置按 URL 的 Bitmap 缓存（内存 4MB + 磁盘 cacheDir/images/）。
 *  首次加载后自动存磁盘，后续启动秒开；App.onCreate 时预加载全部 15 张 PNG。 */
public class ImageLoader {

    /** 需要预下载的心情+徽章 PNG 总数（与 PNG_PATHS 等长） */
    public static final int PNG_COUNT = 15;

    private static final int MAX_CACHE_BYTES = 4 * 1024 * 1024; // 4MB
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private static File diskDir = null;

    /** 初始化磁盘缓存目录（App.onCreate 时调用一次即可） */
    public static void init(Context ctx) {
        diskDir = new File(ctx.getCacheDir(), "images");
        diskDir.mkdirs();
    }

    /** 磁盘缓存文件路径（按 URL 取文件名，如 mood_happy.png） */
    private static File diskFile(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        return new File(diskDir, name);
    }

    /** 从磁盘加载 Bitmap */
    private static Bitmap fromDisk(String url) {
        if (diskDir == null) return null;
        File f = diskFile(url);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** 保存到磁盘缓存 */
    private static void toDisk(String url, byte[] data) {
        if (diskDir == null) return;
        try (FileOutputStream out = new FileOutputStream(diskFile(url))) {
            out.write(data);
        } catch (Exception ignored) { }
    }

    /** 从网络下载图片，返回字节数组 */
    private static byte[] downloadBytes(String url) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoInput(true);
        conn.connect();
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        }
    }

    /** 加载一张图片，设到 ImageView（主线程回调）。优先内存→磁盘→网络，自动缓存。 */
    public static void load(ImageView iv, String url) {
        // 1. 内存缓存
        Bitmap cached = cache.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }

        // 2. 磁盘缓存
        Bitmap disk = fromDisk(url);
        if (disk != null) {
            cache.put(url, disk);
            iv.setImageBitmap(disk);
            return;
        }

        // 3. 网络下载
        Bg.run(() -> {
            try {
                byte[] data = downloadBytes(url);
                toDisk(url, data);
                return BitmapFactory.decodeByteArray(data, 0, data.length);
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

    /** 从缓存取 Bitmap（内存→磁盘，没有返回 null，不触发网络请求） */
    public static Bitmap getCached(String url) {
        Bitmap bm = cache.get(url);
        if (bm != null) return bm;
        return fromDisk(url);
    }

    /** 加载 Bitmap（异步），主线程回调。缓存命中时同步回调。 */
    public static void loadBitmap(String url, Consumer<Bitmap> callback) {
        // 1. 内存缓存
        Bitmap cached = cache.get(url);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 2. 磁盘缓存
        Bitmap disk = fromDisk(url);
        if (disk != null) {
            cache.put(url, disk);
            callback.accept(disk);
            return;
        }

        // 3. 网络下载
        Bg.run(() -> {
            try {
                byte[] data = downloadBytes(url);
                toDisk(url, data);
                return BitmapFactory.decodeByteArray(data, 0, data.length);
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

    // 所有需要预下载的心情+徽章 PNG 路径（相对于 /static/）
    private static final String[] PNG_PATHS = {
        "images/mood_happy.png", "images/mood_calm.png", "images/mood_excited.png",
        "images/mood_grateful.png", "images/mood_tired.png", "images/mood_anxious.png",
        "images/mood_sad.png", "images/mood_angry.png", "images/mood_lonely.png",
        "images/mood_numb.png",
        "images/badge_5.png", "images/badge_30.png", "images/badge_100.png",
        "images/badge_365.png", "images/badge_1000.png",
    };

    /** 同步预加载所有 PNG 到磁盘缓存（SplashActivity 后台线程调用，阻塞直到全部下载完成）。
     *  已存在的跳过，仅下载缺失的。 */
    public static void preloadAllSync(String serverBase) {
        for (String path : PNG_PATHS) {
            String url = serverBase + "/static/" + path;
            if (diskFile(url).exists()) continue;
            try {
                byte[] data = downloadBytes(url);
                toDisk(url, data);
            } catch (Exception ignored) { }
        }
    }

    /** 返回当前磁盘缓存中的 PNG 文件数（用于判断是否首次启动）。 */
    public static int diskCacheCount() {
        if (diskDir == null) return 0;
        File[] files = diskDir.listFiles((d, name) -> name.endsWith(".png"));
        return files == null ? 0 : files.length;
    }

    /** 预加载所有心情+徽章 PNG 到磁盘缓存（App.onCreate 后台调用，不阻塞启动）。
     *  首次启动下载全部 15 张，后续启动秒开。 */
    public static void preloadAll(Context ctx, String serverBase) {
        init(ctx);
        Bg.run(() -> preloadAllSync(serverBase));
    }
}