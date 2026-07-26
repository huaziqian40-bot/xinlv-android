package com.moodtree.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** 本地配置：服务器地址、登录令牌、设备名、游客模式、主题。
 *  键名与 Windows 端一致（serverBase/token/username/guestMode/themeId/accent/device），
 *  Android 用 SharedPreferences 存。 */
public class Config {

    /** 默认服务器（frp 穿透公网地址），用户可在「我的→设置」里改 */
    public static final String DEFAULT_SERVER = "http://sc1.dpfrp.top:12345";

    private final SharedPreferences prefs;

    public Config(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("moodtree", Context.MODE_PRIVATE);
    }

    public String serverBase() {
        String s = prefs.getString("serverBase", DEFAULT_SERVER).trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public void setServerBase(String s) {
        prefs.edit().putString("serverBase", s.trim()).apply();
    }

    public String token() { return prefs.getString("token", ""); }
    public void setToken(String t) { prefs.edit().putString("token", t).apply(); }

    public String username() { return prefs.getString("username", ""); }
    public void setUsername(String u) { prefs.edit().putString("username", u).apply(); }

    /** 游客模式：不登录也能用，数据只在本机；登录后游客期间的记录会随同步上云 */
    public boolean guestMode() { return prefs.getBoolean("guestMode", false); }
    public void setGuestMode(boolean g) { prefs.edit().putBoolean("guestMode", g).apply(); }

    /** 主题预设 id（warm/night/mint/sakura）与自定义强调色（空串 = 用主题默认） */
    public String themeId() { return prefs.getString("themeId", "warm"); }
    public void setThemeId(String id) { prefs.edit().putString("themeId", id).apply(); }

    public String accent() { return prefs.getString("accent", ""); }
    public void setAccent(String hex) { prefs.edit().putString("accent", hex == null ? "" : hex).apply(); }

    /** 设备备注：登录时上报给服务端，方便用户在多台设备间区分令牌 */
    public String device() {
        String d = prefs.getString("device", "").trim();
        if (!d.isEmpty()) return d;
        // 默认用机型 + Android 版本
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    }

    public boolean loggedIn() { return !token().isEmpty(); }

    /** 可以直接进主界面：已登录，或用户选了游客模式 */
    public boolean canEnterMain() { return loggedIn() || guestMode(); }
}
