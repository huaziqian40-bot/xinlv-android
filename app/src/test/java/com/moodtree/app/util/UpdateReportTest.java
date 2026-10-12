package com.moodtree.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 手动「检查更新」结果 → 用户提示语的映射（纯逻辑，不联网、不依赖 Android）。
 *  核心：检查失败与「已是最新版本」是相反状态，绝不混为一谈。 */
public class UpdateReportTest {

    /** 服务端正常响应、且没更新 → 「已是最新版本」。 */
    @Test public void 无新版本_提示已是最新() {
        assertEquals("已是最新版本", UpdateReport.describe(UpdateReport.ManualResult.UP_TO_DATE, ""));
    }

    /** 发现有新版本 → 带上版本号提示，且进既有更新流程。 */
    @Test public void 有新版本_提示发现新版本带版本号() {
        assertEquals("发现新版本 v1.2.35",
                UpdateReport.describe(UpdateReport.ManualResult.UPDATE_AVAILABLE, "1.2.35"));
    }

    /** 关键：检查失败绝不能显示成「已是最新版本」—— 离线/服务不可达 ≠ 已最新。 */
    @Test public void 检查失败_不能当已最新() {
        String msg = UpdateReport.describe(UpdateReport.ManualResult.FAILED, "");
        assertNotEquals("已是最新版本", msg);
        assertEquals("检查失败，请稍后再试", msg);
    }

    /** 失败是独立三态之一，与「已最新」「有新版本」两态都不撞。 */
    @Test public void 三种结果互不相同() {
        String upToDate = UpdateReport.describe(UpdateReport.ManualResult.UP_TO_DATE, "");
        String newVer = UpdateReport.describe(UpdateReport.ManualResult.UPDATE_AVAILABLE, "1.2.35");
        String failed = UpdateReport.describe(UpdateReport.ManualResult.FAILED, "");
        assertTrue(!upToDate.equals(newVer) && !upToDate.equals(failed) && !newVer.equals(failed));
    }

    /** 空版本号不拼出“v”bug（防御性）。 */
    @Test public void 新版本号为空_不崩() {
        assertEquals("发现新版本 v", UpdateReport.describe(UpdateReport.ManualResult.UPDATE_AVAILABLE, null));
    }
}