package com.moodtree.app.sync;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** WatchDecision 换路判断的纯逻辑单测（不联网、不依赖 Android 运行时）。 */
public class WatchDecisionTest {

    /** changed=true → 立即重挂（调用方会先跑一轮同步），不睡。 */
    @Test public void changed_true_则同步并立即重挂() {
        assertEquals(0L, WatchDecision.nextWaitMs(true, -1, false, WatchDecision.ERROR_BACKOFF_MS));
        // 即便带了 retry_after 也按 changed 处理：调用方先同步，这里只管下一步马上继续
        assertEquals(0L, WatchDecision.nextWaitMs(true, 0, false, WatchDecision.ERROR_BACKOFF_MS));
    }

    /** changed=false 且无 retry_after = 普通超时：正常长轮询链，立即重挂、不睡。 */
    @Test public void false_无retry_是普通超时_立即重挂() {
        assertEquals(0L, WatchDecision.nextWaitMs(false, -1, false, WatchDecision.ERROR_BACKOFF_MS));
        assertEquals(0L, WatchDecision.nextWaitMs(false, 0, false, WatchDecision.ERROR_BACKOFF_MS));
    }

    /** changed=false 带 retry_after：按它等足，绝不紧循环重发。 */
    @Test public void false_带retry_按值等待_不紧循环() {
        assertEquals(3000L, WatchDecision.nextWaitMs(false, 3, false, WatchDecision.ERROR_BACKOFF_MS));
        // 至少 1 秒，从根上杜绝紧循环
        assertEquals(1000L, WatchDecision.nextWaitMs(false, 1, false, WatchDecision.ERROR_BACKOFF_MS));
    }

    /** 异常/断线 → 退避重试，环路存活；退避基数被尊重、且不小于 1 秒。 */
    @Test public void 异常_退避且环路存活() {
        assertEquals(3000L, WatchDecision.nextWaitMs(false, -1, true, 3000L));
        assertEquals(5000L, WatchDecision.nextWaitMs(false, -1, true, 5000L));
        // 小于 1 秒的退避被上抬，防止紧循环
        assertEquals(1000L, WatchDecision.nextWaitMs(false, -1, true, 100L));
    }
}