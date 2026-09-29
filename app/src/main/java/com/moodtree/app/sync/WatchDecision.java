package com.moodtree.app.sync;

/** watch 环路的"下一步等多久"判断 —— 纯函数，不碰网络也不碰线程，方便单元测试。
 *  语义与 phix 客户端的 #watchLoop / _watch_loop 一致：
 *    - 出错/断线 → 退避几秒再重挂，绝不转成紧循环；
 *    - 服务端忙给出 retry_after → 按它的值等足，这**不是**超时，绝不能在紧循环里重发；
 *    - changed=true / changed=false 且无 retry_after（普通超时）→ 立即重挂，这是正常的长轮询链。 */
public final class WatchDecision {

    /** 出错/断线后的退避基数（毫秒）。调用方可按需传自己的值，这里只是默认。 */
    public static final long ERROR_BACKOFF_MS = 3_000L;
    /** retry_after / 退避的最小尊重时长（毫秒）—— 永远不小于 1 秒，从根上杜绝紧循环。 */
    public static final long MIN_WAIT_MS = 1_000L;

    private WatchDecision() {}

    /** 决定下一次 watch 请求前要等多久。
     *  @param changed        服务端说 mood 数据变了？
     *  @param retryAfterSec  服务端给的 retry_after（秒）；&lt;=0 表示没有
     *  @param error          这次请求异常/失败（网络层、超时、HTTP 错误）
     *  @param errorBackoffMs 出错时的退避基数（毫秒）；小于 1 秒会被上抬，防止紧循环
     *  @return 下一次 watch 请求前应等待的毫秒数；0 = 立即重挂
     *    - error        → 退避 errorBackoffMs（至少 1 秒）
     *    - retry_after  → 等足 retry_after 秒（至少 1 秒）
     *    - changed / 普通超时 → 0：都是正常长轮询链，立即重挂（changed 时调用方会先跑一轮同步） */
    public static long nextWaitMs(boolean changed, int retryAfterSec, boolean error, long errorBackoffMs) {
        if (error) {
            return Math.max(MIN_WAIT_MS, errorBackoffMs);
        }
        if (retryAfterSec > 0) {
            return Math.max(MIN_WAIT_MS, retryAfterSec * 1000L);
        }
        return 0;
    }
}
