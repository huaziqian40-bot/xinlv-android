package com.moodtree.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 后台线程跑任务，结果回到主线程刷新 UI（替代 JavaFX 的 Bg.run）。 */
public class Bg {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public interface Callback<T> { void ok(T result); }
    public interface Err { void fail(Exception e); }

    /** 后台跑 task，成功调 onOk（主线程），异常调 onErr（主线程） */
    public static <T> void run(java.util.concurrent.Callable<T> task, Callback<T> onOk, Err onErr) {
        POOL.execute(() -> {
            try {
                T r = task.call();
                UI.post(() -> onOk.ok(r));
            } catch (Exception e) {
                UI.post(() -> { if (onErr != null) onErr.fail(e); });
            }
        });
    }

    /** 后台跑无返回值任务 */
    public static void run(Runnable task) {
        POOL.execute(task);
    }

    /** 主线程回调 */
    public static void ui(Runnable task) {
        UI.post(task);
    }
}
