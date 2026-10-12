package com.moodtree.app.util;

/** 手动「检查更新」在 UI 上要说的那句话 —— 纯逻辑、不碰 Android 也不碰网络，方便单测。
 *
 *  最关键的一点：检查失败（离线 / 服务不可达 / 响应异常）是**相反**于「已是最新版本」的状态，
 *  绝对不能混为一谈 —— 这正是这类开关最常见的坑：离线时把「检查失败」当成「已是最新」，
 *  用户就会以为已经是最新，再也不主动更新了。 */
public final class UpdateReport {

    private UpdateReport() { }

    /** 一次手动「检查更新」的结果分类。 */
    public enum ManualResult {
        /** 服务端正常响应，且没有比当前更新的版本 */
        UP_TO_DATE,
        /** 服务端返回了一个更新的版本 */
        UPDATE_AVAILABLE,
        /** 检查没跑通（离线 / 服务不可达 / 响应异常） */
        FAILED
    }

    /** 把检查结果翻译成给用户看的一句话。
     *
     *  @return 三态之一：已最新 / 发现新版本 / 检查失败
     *
     *  这里用枚举显式排布三种结果，从结构上杜绝「失败当最新」：
     *  UP_TO_DATE 和 FAILED 是独立的两个分支，谁也不会吞掉谁。 */
    public static String describe(ManualResult result, String newVersion) {
        switch (result) {
            case UPDATE_AVAILABLE:
                return "发现新版本 v" + (newVersion == null ? "" : newVersion);
            case UP_TO_DATE:
                return "已是最新版本";
            case FAILED:
            default:
                // 失败与「已是最新」不同：不弹「已是最新」，而提示重试
                return "检查失败，请稍后再试";
        }
    }
}
