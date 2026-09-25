package com.moodtree.app.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 心履 Android 应用内自更新（国内分发，无 Google Play）。
 *  - 启动时后台静默检查 /api/v1/update/check?product=xinlv&platform=android
 *  - 有新版 → 弹 Toast 提示 + 自动下载 APK（后台线程）
 *  - 下载完成 → 校验 SHA256 → FileProvider 暴露 → ACTION_VIEW 调起系统安装器
 * 权限：Manifest 需 REQUEST_INSTALL_PACKAGES；Android 8+ 首次需引导开启
 * "允许安装未知来源"。数据/版本从 Config 读。 */
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

    /** 后台检查更新。有新版时自动下载并在下载完成后调起安装器。 */
    public static void checkAndUpdate(Context ctx) {
        Bg.run(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(CHECK_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Xinlv-Android-" + currentVersion(ctx));
                int code = conn.getResponseCode();
                if (code != 200) return;
                byte[] body = readAll(conn.getInputStream());
                String json = new String(body, StandardCharsets.UTF_8);

                String latest = jsonString(json, "latest_version");
                String url = jsonString(json, "url");
                String sha = jsonString(json, "sha256");
                if (latest.isEmpty() || url.isEmpty() || sha.isEmpty()) return;
                if (latest.equals(currentVersion(ctx))) return;

                // 有新版：下载到缓存目录
                File apk = new File(ctx.getCacheDir(), "xinlv-update.apk");
                if (apk.exists()) apk.delete();
                download(url, apk);
                if (!sha256(apk).equalsIgnoreCase(sha)) {
                    apk.delete();
                    return;
                }
                // 主线程提示并调起安装
                final File f = apk;
                Bg.ui(() -> {
                    if (!canInstall(ctx)) {
                        Toast.makeText(ctx, "请先允许安装来自此来源的应用，再点击更新", Toast.LENGTH_LONG).show();
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + ctx.getPackageName()));
                        ctx.startActivity(i);
                        return;
                    }
                    install(ctx, f);
                });
            } catch (Exception e) {
                Log.w(TAG, "update check failed", e);
            }
        });
    }

    private static boolean canInstall(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            PackageManager pm = ctx.getPackageManager();
            return pm.canRequestPackageInstalls();
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
        }
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
