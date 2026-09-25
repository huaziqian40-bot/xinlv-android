package com.moodtree.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.moodtree.app.R;
import com.moodtree.app.model.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 心履 Android 应用内自更新（国内分发，无 Google Play）。
 *
 *  流程：进入软件时后台检查 /api/v1/update/check?product=xinlv&platform=android
 *  → 有新版本且未被用户「跳过本版本」→ 弹更新卡片（版本号 + 更新内容 + 三个按钮）：
 *      · 取消        这次先不选，继续用软件（下次启动仍会提示）
 *      · 跳过本版本  记住该版本，之后不再提示（更高版本照常提示）
 *      · 更新        下载 APK → SHA256 校验 → FileProvider → 调起系统安装器
 *
 *  权限：Manifest 需 REQUEST_INSTALL_PACKAGES；Android 8+ 首次需引导开启
 *  "允许安装未知来源"。最后一次「安装」确认由系统强制，应用无法绕过。 */
public final class Updater {

    private static final String TAG = "XinlvUpdater";
    private static final String CHECK_URL =
            "https://phix.ing/api/v1/update/check?product=xinlv&platform=android";

    private Updater() { }

    /** 当前应用版本名（versionName），例如 "1.2.31" */
    public static String currentVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** 接口返回的一条更新信息 */
    private static final class Info {
        String version = "";
        String url = "";
        String sha256 = "";
        String notes = "";
    }

    /**
     * 进入软件时调用：后台检查更新；有新版本（且用户没跳过该版本）就弹更新卡片。
     * 不自动下载 —— 由用户在卡片上选「更新」后才下载安装。
     */
    public static void checkAndShowCardIfNeeded(Activity activity) {
        Bg.run(() -> {
            try {
                Info info = fetchInfo(activity);
                if (info == null) return;
                Config config = ((com.moodtree.app.App) activity.getApplication()).config();
                // 用户点过「跳过本版本」→ 该版本不再提示
                if (info.version.equals(config.skippedUpdateVersion())) return;
                Bg.ui(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    showUpdateCard(activity, info, config);
                });
            } catch (Exception e) {
                Log.w(TAG, "update check failed", e);
            }
        });
    }

    /** 拉取版本信息；无更新或字段不全时返回 null。 */
    private static Info fetchInfo(Context ctx) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(CHECK_URL).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Xinlv-Android-" + currentVersion(ctx));
        if (conn.getResponseCode() != 200) return null;
        String json = new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);

        Info info = new Info();
        info.version = jsonString(json, "latest_version");
        info.url = jsonString(json, "url");
        info.sha256 = jsonString(json, "sha256");
        info.notes = jsonString(json, "release_notes");
        if (info.version.isEmpty() || info.url.isEmpty() || info.sha256.isEmpty()) return null;
        if (info.version.equals(currentVersion(ctx))) return null;
        return info;
    }

    /** 弹更新卡片：版本号 + 更新内容 + 取消 / 跳过本版本 / 更新。 */
    private static void showUpdateCard(Activity activity, Info info, Config config) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);

        TextView tvVersion = view.findViewById(R.id.tvUpdateVersion);
        TextView tvNotesLabel = view.findViewById(R.id.tvUpdateNotesLabel);
        TextView tvNotes = view.findViewById(R.id.tvUpdateNotes);
        TextView tvHint = view.findViewById(R.id.tvUpdateHint);
        TextView btnCancel = view.findViewById(R.id.btnUpdateCancel);
        TextView btnSkip = view.findViewById(R.id.btnUpdateSkip);
        TextView btnNow = view.findViewById(R.id.btnUpdateNow);

        // 主题色（与全站一致：BG / CARD / INK / INK_SOFT / ACCENT）
        tvVersion.setTextColor(Theme.ACCENT);
        tvVersion.setText("新版本 v" + info.version + "（当前 v" + currentVersion(activity) + "）");
        tvNotesLabel.setTextColor(Theme.INK_SOFT);
        tvNotes.setTextColor(Theme.INK);
        tvNotes.setText(info.notes == null || info.notes.isEmpty() ? "稳定性与体验改进。" : info.notes);
        tvHint.setTextColor(Theme.INK_SOFT);
        btnCancel.setTextColor(Theme.INK_SOFT);
        btnSkip.setTextColor(Theme.INK_SOFT);
        btnNow.setTextColor(contrastOn(Theme.ACCENT));
        btnNow.setBackground(roundRect(Theme.ACCENT, 10, activity));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(roundRect(Theme.CARD, 16, activity));
        }

        // 取消：这次先不选，继续用软件（不记住任何东西，下次启动还会提示）
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        // 跳过本版本：记住该版本，之后不再提示（更高的版本照常提示）
        btnSkip.setOnClickListener(v -> {
            config.setSkippedUpdateVersion(info.version);
            Toast.makeText(activity, "已跳过 v" + info.version, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        // 更新：下载 → 校验 → 调起系统安装器
        btnNow.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(activity, "正在下载 v" + info.version + "…", Toast.LENGTH_SHORT).show();
            downloadAndInstall(activity, info);
        });

        dialog.show();
    }

    /** 下载 APK → SHA256 校验 → FileProvider → ACTION_VIEW 安装。 */
    private static void downloadAndInstall(Activity activity, Info info) {
        final Context appCtx = activity.getApplicationContext();
        Bg.run(() -> {
            try {
                File apk = new File(appCtx.getCacheDir(), "xinlv-update.apk");
                if (apk.exists() && !apk.delete()) {
                    Log.w(TAG, "无法删除旧的下载文件");
                }
                download(info.url, apk);
                if (!sha256(apk).equalsIgnoreCase(info.sha256)) {
                    // 坏包（传输损坏或被篡改）：丢弃，不安装
                    apk.delete();
                    Bg.ui(() -> Toast.makeText(appCtx, "更新包校验失败，已取消安装", Toast.LENGTH_LONG).show());
                    return;
                }
                final File ready = apk;
                Bg.ui(() -> {
                    if (!canInstall(appCtx)) {
                        Toast.makeText(appCtx, "请先允许安装来自此来源的应用，再点更新", Toast.LENGTH_LONG).show();
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + appCtx.getPackageName()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            appCtx.startActivity(i);
                        } catch (Exception ignored) { }
                        return;
                    }
                    install(appCtx, ready);
                });
            } catch (Exception e) {
                Log.w(TAG, "download failed", e);
                Bg.ui(() -> Toast.makeText(appCtx, "下载失败，请检查网络后重试", Toast.LENGTH_LONG).show());
            }
        });
    }

    private static boolean canInstall(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            return ctx.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private static void install(Context ctx, File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "install failed", e);
            Toast.makeText(ctx, "无法调起安装器，请手动到官网下载更新", Toast.LENGTH_LONG).show();
        }
    }

    // ---- 工具 ----

    private static GradientDrawable roundRect(int color, int radiusDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = radiusDp * ctx.getResources().getDisplayMetrics().density;
        d.setCornerRadius(r);
        return d;
    }

    /** 在给定底色上取可读的前景色（浅底用深字，深底用浅字）。 */
    private static int contrastOn(int bg) {
        double lum = (0.299 * ((bg >> 16) & 0xFF) + 0.587 * ((bg >> 8) & 0xFF) + 0.114 * (bg & 0xFF)) / 255.0;
        return lum > 0.6 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static String jsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        i = json.indexOf(':', i + key.length() + 2);
        if (i < 0) return "";
        i = json.indexOf('"', i);
        if (i < 0) return "";
        int j = json.indexOf('"', i + 1);
        if (j < 0) return "";
        return json.substring(i + 1, j);
    }

    private static String sha256(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
