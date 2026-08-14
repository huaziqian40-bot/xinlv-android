package com.moodtree.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.moodtree.app.model.Theme;

/** 合成大西瓜小游戏：用 WebView 加载 assets/game.html（纯 Canvas JS 游戏，与网页端一致）。
 *  游戏逻辑完全在 HTML 中，Fragment 只负责 WebView 配置和生命周期管理。 */
public class GameFragment extends BaseFragment {

    private WebView webView;
    private final Handler configHandler = new Handler(Looper.getMainLooper());
    private boolean viewAlive;
    private final Runnable configPoller = new Runnable() {
        @Override public void run() {
            if (!viewAlive || webView == null) return;
            com.moodtree.app.util.Bg.run(() -> app().api().gameConfig(),
                    config -> {
                        if (viewAlive && webView != null) {
                            String json = config.toString().replace("\\", "\\\\").replace("'", "\\'");
                            webView.evaluateJavascript("window.applyGameConfig && window.applyGameConfig(" + json + ");", null);
                        }
                        if (viewAlive) configHandler.postDelayed(this, 60000);
                    }, error -> { if (viewAlive) configHandler.postDelayed(this, 60000); });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_game, container, false);
        themeBackground(root);
        webView = root.findViewById(R.id.gameWebView);
        viewAlive = true;

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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectThemeColors();
            }
        });
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setBackgroundColor(Color.TRANSPARENT);

        // 加载 assets 中的游戏页面
        webView.loadUrl("file:///android_asset/game.html");
        configHandler.post(configPoller);
        return root;
    }

    /** 向 WebView 注入当前主题颜色 */
    private void injectThemeColors() {
        String bg = Theme.toHex(Theme.BG);
        String card = Theme.toHex(Theme.CARD);
        String accent = Theme.toHex(Theme.ACCENT);
        String ink = Theme.toHex(Theme.INK);
        String inkSoft = Theme.toHex(Theme.INK_SOFT);
        String canvasBg = Theme.toHex(Theme.isDark(Theme.BG) ? Theme.CARD : Theme.blend(Theme.CARD, 0xFFFFFF, 0.5f));
        String js = String.format(
            "javascript:window.applyTheme('%s','%s','%s','%s','%s','%s');",
            bg, card, accent, ink, inkSoft, canvasBg
        );
        webView.evaluateJavascript(js, null);
    }

    @Override
    public void onDestroyView() {
        viewAlive = false;
        configHandler.removeCallbacksAndMessages(null);
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