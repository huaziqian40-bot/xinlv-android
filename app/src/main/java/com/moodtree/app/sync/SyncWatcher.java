package com.moodtree.app.sync;

import android.util.Log;

import com.google.gson.JsonObject;
import com.moodtree.app.db.AppDatabase;
import com.moodtree.app.util.ApiClient;
import com.moodtree.app.util.Config;

/** 前台长轮询同步：对 /api/v1/sync/watch/ 挂着等服务端，云端一变立刻跑一轮完整同步。
 *
 *  **只在应用处于前台时启动**（由 MainActivity 的 onStart/onStop 驱动）。为什么不在后台跑：
 *  Android 会杀掉后台进程里挂着的长连接，留着既白耗电又白耗流量——所以"仅前台"才是诚实的设计。
 *  回前台掉队的变化由 MainActivity 补跑一轮完整同步兜住，这里只管前台期间秒级叫醒。
 *
 *  **关键陷阱（长轮询最容易踩的坑）**：服务端 hold 约 {@link #SERVER_HOLD_SEC}s，客户端的
 *  **读超时必须略长于 hold**（这里 35s）——否则服务端刚要应答，客户端自己就把连接掐断了，
 *  永远拿不到变化。读超时太短 = 长轮询退化成"看起来像长轮询、其实一秒一次"的空轮询。
 *
 *  环路里任何失败都被吞掉、按 {@link WatchDecision} 退避重试，绝不影响正常的
 *  周期性/手动同步路径。 */
public class SyncWatcher {

    private static final String TAG = "SyncWatcher";

    /** 服务端单次 hold 的时长（秒）。 */
    static final int SERVER_HOLD_SEC = 25;
    /** 客户端读超时（毫秒），**必须略长于** SERVER_HOLD_SEC，见类头注释。 */
    static final long READ_TIMEOUT_MS = (SERVER_HOLD_SEC + 10) * 1000L;   // 35s > 25s
    /** 出错/断线后的退避基数。 */
    static final long ERROR_BACKOFF_MS = WatchDecision.ERROR_BACKOFF_MS;

    /** 游标在本地 KV 里的键：跨启动还记得上次挂到哪（服务端下发的不透明字符串，客户端只回显）。 */
    static final String CURSOR_KEY = "watch_cursor";

    private final Config config;
    private final ApiClient api;
    private final AppDatabase db;

    private volatile boolean stopped = true;
    private volatile Thread thread;

    // ---- 诊断状态（镜像 phix 客户端的 watching / last_watch_hit_at）----
    private volatile long lastWatchHitAt;
    private volatile String watchCursor = "";

    public SyncWatcher(Config config, ApiClient api, AppDatabase db) {
        this.config = config;
        this.api = api;
        this.db = db;
    }

    /** 是否正在跑 watch 环路（诊断用）。 */
    public boolean isWatching() {
        Thread t = thread;
        return !stopped && t != null && t.isAlive();
    }

    /** 最近一次被云端叫醒的时间戳（epoch 毫秒；0 = 从未被叫醒），诊断用。 */
    public long lastWatchHitAt() { return lastWatchHitAt; }

    /** 当前回显给服务端的光标，诊断用。 */
    public String watchCursor() { return watchCursor; }

    /** 在专用后台线程上启动环路（幂等）。未登录不启动，不向服务器乱发请求。 */
    public void start() {
        if (!config.loggedIn()) return;                 // 未登录：不向未授权账号发请求
        if (isWatching()) return;
        stopped = false;
        // 从本地 KV 恢复上次的光标（如果之前停过/退出过）
        try {
            String c = db.kvDao().get(CURSOR_KEY);
            if (c != null) watchCursor = c;
        } catch (Exception ignored) { }
        Thread t = new Thread(this::runLoop, "sync-watch");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    /** 停止环路（登出 / 退后台 / 销毁时调用；幂等，不泄露线程）。
     *  正在挂着的某次请求会在其读超时（35s）内自然收尾，之后线程退出、不再发新请求。 */
    public void stop() {
        stopped = true;
        Thread t = thread;
        if (t != null) {
            t.interrupt();      // 若线程正睡在退避上，让它立刻醒过来检查 stopped
            thread = null;
        }
    }

    private void runLoop() {
        while (!stopped) {
            boolean changed = false;
            int retryAfterSec = -1;
            boolean errored = false;
            try {
                JsonObject resp = api.watchEntries(watchCursor, (int) READ_TIMEOUT_MS);
                retryAfterSec = resp.has("retry_after") ? resp.get("retry_after").getAsInt() : -1;
                changed = resp.has("changed") && resp.get("changed").getAsBoolean();
                if (resp.has("cursor")) watchCursor = resp.get("cursor").getAsString();
                if (changed) {
                    lastWatchHitAt = System.currentTimeMillis();
                    // 服务端说变了：先把游标落盘，再立刻跑一轮正常同步（推+拉）把变化收下来
                    persistCursor();
                    runSync();
                }
            } catch (ApiClient.ApiException e) {
                if (e.status == 404) {
                    // 老服务端没有 /sync/watch/：安静降级到原有同步行为，别再反复打
                    Log.i(TAG, "服务端不支持 watch 长轮询（404），沿用原有同步");
                    break;
                }
                errored = true;
                Log.w(TAG, "watch 失败（status=" + e.status + "）：" + e.getMessage());
            } catch (Exception e) {
                errored = true;
                Log.w(TAG, "watch 异常：" + e.getMessage());
            }
            if (stopped) break;
            long waitMs = WatchDecision.nextWaitMs(changed, retryAfterSec, errored, ERROR_BACKOFF_MS);
            sleepQuietly(waitMs);
        }
        stopped = true;
    }

    /** 复用普通同步路径；未登录/离线都安全返回，任何失败都被吞掉，绝不影响 watch 环路本身。 */
    private void runSync() {
        try {
            new SyncEngine(config, api, db).sync();
        } catch (Exception ignored) { }
    }

    private void persistCursor() {
        try { db.kvDao().set(CURSOR_KEY, watchCursor); } catch (Exception ignored) { }
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 保住中断标志，让外层 while 检查 stopped 退出
        }
    }
}