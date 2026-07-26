package com.moodtree.app.util;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** 系统 UI 避让：给内容视图顶部留出状态栏高度，底部留出导航栏/手势条高度。
 *  各 Activity setContentView 后对根布局调用，避免内容被系统栏遮住。 */
public class Insets {

    /** 顶部避让状态栏（登录页、主界面等顶部有内容的页面） */
    public static void applyTop(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /** 顶部避让状态栏 + 底部避让导航栏（主界面：顶内容 + 底部 BottomNav 之上还有手势条） */
    public static void applyTopAndBottom(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }
}
