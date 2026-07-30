package com.moodtree.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.moodtree.app.R;

/** 合成大西瓜小游戏：用 WebView 加载 assets/game.html（纯 Canvas JS 游戏，与网页端一致）。
 *  游戏逻辑完全在 HTML 中，Fragment 只负责 WebView 配置和生命周期管理。 */
public class GameFragment extends BaseFragment {

    private WebView webView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_game, container, false);
        webView = root.findViewById(R.id.gameWebView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);                // localStorage 存最高分
        ws.setAllowFileAccess(true);                  // 加载 assets 中图片
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        // 触控优化
        ws.setSupportZoom(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // 加载 assets 中的游戏页面
        webView.loadUrl("file:///android_asset/game.html");
        return root;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    /** 返回键处理：让 WebView 可以消费返回键 */
    public boolean onBackPressed() {
        return false; // 游戏内无页面栈，不消费
    }
}