package com.moodtree.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.moodtree.app.App;
import com.moodtree.app.R;
import com.moodtree.app.model.Theme;
import com.moodtree.app.sync.SyncEngine;
import com.moodtree.app.util.ApiClient;
import com.moodtree.app.util.Bg;

/** 登录/注册页：顶部切换标签；注册多确认密码和免责声明勾选，成功后直接进主界面。
 *  不想注册可点游客模式。服务器地址不在这里暴露（藏在 我的→设置）。 */
public class LoginActivity extends AppCompatActivity {

    private App app;
    private EditText etUser, etPass, etPass2;
    private CheckBox cbAgree;
    private TextView tvStatus, tvDisclaimer, tvGuest;
    private Button btnTabLogin, btnTabRegister, btnSubmit;
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (App) getApplication();

        // 已登录或游客模式 → 直接进主界面，不显示登录页
        if (app.config().canEnterMain()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        // 顶部避让状态栏，内容不被系统 UI 遮挡
        com.moodtree.app.util.Insets.applyTop(findViewById(android.R.id.content));
        applyThemeToViews();

        etUser = findViewById(R.id.etUsername);
        etPass = findViewById(R.id.etPassword);
        etPass2 = findViewById(R.id.etPassword2);
        cbAgree = findViewById(R.id.cbAgree);
        tvStatus = findViewById(R.id.tvStatus);
        tvDisclaimer = findViewById(R.id.tvDisclaimer);
        tvGuest = findViewById(R.id.tvGuest);
        btnTabLogin = findViewById(R.id.btnTabLogin);
        btnTabRegister = findViewById(R.id.btnTabRegister);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnTabLogin.setOnClickListener(v -> setMode(false));
        btnTabRegister.setOnClickListener(v -> setMode(true));
        btnSubmit.setOnClickListener(v -> submit());
        tvGuest.setOnClickListener(v -> {
            app.config().setGuestMode(true);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        tvDisclaimer.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(app.config().serverBase() + "/disclaimer/")));
            } catch (Exception e) {
                tvStatus.setText("打不开浏览器，免责声明可在网页版查看");
            }
        });

        setMode(false);

        // 表单内容淡入动画
        View content = findViewById(R.id.content);
        if (content != null) {
            content.setAlpha(0f);
            content.setTranslationY(dp(30));
            content.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(400)
                    .setStartDelay(100)
                    .start();
        }
    }

    private void setMode(boolean register) {
        registerMode = register;
        etPass2.setVisibility(register ? View.VISIBLE : View.GONE);
        findViewById(R.id.agreeRow).setVisibility(register ? View.VISIBLE : View.GONE);
        btnSubmit.setText(register ? "注 册" : "登 录");
        tvStatus.setText("");
        styleTab(btnTabLogin, !register);
        styleTab(btnTabRegister, register);
    }

    private void styleTab(Button b, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(10));
        if (active) {
            gd.setColor(Theme.ACCENT);
            b.setTextColor(0xFFFFFFFF);
        } else {
            gd.setColor(0x00000000);
            b.setTextColor(Theme.INK_SOFT);
        }
        gd.setStroke(dp(1), active ? Theme.ACCENT : Theme.DIVIDER);
        b.setBackground(gd);
    }

    private void submit() {
        String name = etUser.getText().toString().trim();
        String pw = etPass.getText().toString();
        if (registerMode) {
            if (!pw.equals(etPass2.getText().toString())) {
                tvStatus.setText("两次输入的密码不一样");
                return;
            }
            if (!cbAgree.isChecked()) {
                tvStatus.setText("请先勾选同意《免责声明》");
                return;
            }
        }
        btnSubmit.setEnabled(false);
        tvStatus.setText(registerMode ? "正在注册…" : "正在登录…");
        Bg.run(() -> {
                    JsonObject r = registerMode
                            ? app.api().register(name, pw)
                            : app.api().login(name, pw);
                    app.config().setToken(r.get("token").getAsString());
                    app.config().setUsername(r.get("username").getAsString());
                    app.config().setGuestMode(false);   // 转正：不再是游客
                    // 离线推荐用的内容缓存 + 游客期间记录补传
                    new SyncEngine(app.config(), app.api(), app.db()).refreshCatalog();
                    new SyncEngine(app.config(), app.api(), app.db()).sync();
                    return null;
                },
                ok -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                },
                err -> {
                    btnSubmit.setEnabled(true);
                    String msg = err instanceof ApiClient.ApiException
                            ? err.getMessage()
                            : (registerMode ? "注册失败：" : "登录失败：") + err.getMessage();
                    tvStatus.setText(msg);
                });
    }

    private void applyThemeToViews() {
        findViewById(android.R.id.content).setBackgroundColor(Theme.BG);
        // 品牌名
        TextView tvBrandName = findViewById(R.id.tvBrandName);
        tvBrandName.setTextColor(Theme.INK);
        TextView tvBrandSub = findViewById(R.id.tvBrandSub);
        tvBrandSub.setTextColor(Theme.INK_SOFT);
        // 表单卡片背景
        View content = findViewById(R.id.content);
        content.setBackground(Theme.createCardBg(getResources().getDisplayMetrics().density, 14));
        // 输入框
        EditText etUser = findViewById(R.id.etUsername);
        etUser.setBackground(Theme.createInputBg());
        etUser.setTextColor(Theme.INK);
        etUser.setHintTextColor(Theme.INK_SOFT);
        EditText etPass = findViewById(R.id.etPassword);
        etPass.setBackground(Theme.createInputBg());
        etPass.setTextColor(Theme.INK);
        etPass.setHintTextColor(Theme.INK_SOFT);
        EditText etPass2 = findViewById(R.id.etPassword2);
        etPass2.setBackground(Theme.createInputBg());
        etPass2.setTextColor(Theme.INK);
        etPass2.setHintTextColor(Theme.INK_SOFT);
        // 提交按钮
        findViewById(R.id.btnSubmit).setBackground(Theme.createPrimaryButton());
        // 同意文本
        TextView tvAgreeText = findViewById(R.id.tvAgreeText);
        tvAgreeText.setTextColor(Theme.INK_SOFT);
        TextView tvDisclaimer = findViewById(R.id.tvDisclaimer);
        tvDisclaimer.setTextColor(Theme.ACCENT);
        // 状态文本
        TextView tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setTextColor(Theme.INK_SOFT);
        // 游客入口
        TextView tvGuest = findViewById(R.id.tvGuest);
        tvGuest.setTextColor(Theme.ACCENT);
        TextView tvGuestHint = findViewById(R.id.tvGuestHint);
        tvGuestHint.setTextColor(Theme.INK_SOFT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        // 登录页不允许返回退出（避免误触关 App），与启动页语义一致
    }
}