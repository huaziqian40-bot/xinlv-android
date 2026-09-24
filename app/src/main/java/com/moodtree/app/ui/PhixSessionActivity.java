package com.moodtree.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.moodtree.app.App;
import com.moodtree.app.R;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Bg;

/** phix 首启引导（CONTRACT §7）：「已有 phix 会话凭据吗？」→ 有→登录 / 没有→注册+跳过 / 跳过
 *  完成后标记 phixSessionDone=true，进入原有启动流程。 */
public class PhixSessionActivity extends AppCompatActivity {

    private App app;

    // 询问页组件
    private TextView tvTitle, tvHint, tvSkip, tvStatus;
    private Button btnHave, btnNone, btnSubmit, btnBack;
    private EditText etServer, etUsername, etPassword, etPassword2;
    private View labelServer;
    private boolean isLoginMode;   // true=登录, false=注册

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (App) getApplication();

        // 已完成引导 → 直接跳过
        if (app.config().phixSessionDone()) {
            goNext();
            return;
        }

        setContentView(R.layout.activity_phix_session);
        com.moodtree.app.util.Insets.applyTop(findViewById(android.R.id.content));
        applyTheme();

        // 绑定组件
        tvTitle = findViewById(R.id.tvTitle);
        tvHint = findViewById(R.id.tvHint);
        tvSkip = findViewById(R.id.tvSkip);
        tvStatus = findViewById(R.id.tvStatus);
        btnHave = findViewById(R.id.btnHave);
        btnNone = findViewById(R.id.btnNone);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnBack = findViewById(R.id.btnBack);
        etServer = findViewById(R.id.etServer);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etPassword2 = findViewById(R.id.etPassword2);
        labelServer = findViewById(R.id.labelServer);

        btnHave.setOnClickListener(v -> showLogin());
        btnNone.setOnClickListener(v -> showRegister());
        btnSubmit.setOnClickListener(v -> submit());
        btnBack.setOnClickListener(v -> showInquiry());
        tvSkip.setOnClickListener(v -> finish引导());

        showInquiry();
    }

    // ---- 询问页 ----

    private void showInquiry() {
        tvTitle.setText("已有 phix 会话凭据吗？");
        tvTitle.setVisibility(View.VISIBLE);
        tvHint.setVisibility(View.VISIBLE);
        btnHave.setVisibility(View.VISIBLE);
        btnNone.setVisibility(View.VISIBLE);
        btnSubmit.setVisibility(View.GONE);
        btnBack.setVisibility(View.GONE);
        etServer.setVisibility(View.GONE);
        labelServer.setVisibility(View.GONE);
        etUsername.setVisibility(View.GONE);
        etPassword.setVisibility(View.GONE);
        etPassword2.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        tvSkip.setVisibility(View.VISIBLE);
    }

    // ---- 登录页 ----

    private void showLogin() {
        isLoginMode = true;
        tvTitle.setText("phix 登录");
        tvHint.setVisibility(View.GONE);
        btnHave.setVisibility(View.GONE);
        btnNone.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.VISIBLE);
        btnSubmit.setText("登 录");
        btnBack.setVisibility(View.VISIBLE);
        etServer.setVisibility(View.VISIBLE);
        labelServer.setVisibility(View.VISIBLE);
        etServer.setText("http://192.168.5.41:8931");
        etUsername.setVisibility(View.VISIBLE);
        etPassword.setVisibility(View.VISIBLE);
        etPassword2.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("");
        tvSkip.setVisibility(View.VISIBLE);
    }

    // ---- 注册页 ----

    private void showRegister() {
        isLoginMode = false;
        tvTitle.setText("phix 注册");
        tvHint.setVisibility(View.GONE);
        btnHave.setVisibility(View.GONE);
        btnNone.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.VISIBLE);
        btnSubmit.setText("注 册");
        btnBack.setVisibility(View.VISIBLE);
        etServer.setVisibility(View.VISIBLE);
        labelServer.setVisibility(View.VISIBLE);
        etServer.setText("http://192.168.5.41:8931");
        etUsername.setVisibility(View.VISIBLE);
        etPassword.setVisibility(View.VISIBLE);
        etPassword2.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("");
        tvSkip.setVisibility(View.VISIBLE);
    }

    // ---- 提交登录/注册 ----

    private void submit() {
        String srv = etServer.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString();
        String pass2 = etPassword2.getText().toString();

        if (srv.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            tvStatus.setText("请填写完整信息");
            return;
        }
        if (!isLoginMode && !pass.equals(pass2)) {
            tvStatus.setText("两次输入的密码不一样");
            return;
        }

        btnSubmit.setEnabled(false);
        tvStatus.setText(isLoginMode ? "正在登录…" : "正在注册…");

        Bg.run(() -> {
            try {
                com.google.gson.JsonObject r = isLoginMode
                        ? app.api().phixLogin(srv, user, pass)
                        : app.api().phixRegister(srv, user, pass);
                String tok = r.has("token") ? r.get("token").getAsString() : "";
                app.config().setToken(tok);
                app.config().setUsername(user);
                app.config().setServerBase(srv);
                app.config().setGuestMode(false);
                // 验证连通
                try { app.api().phixVerifyManifest(srv, tok); } catch (Exception ignored) { }
                return null;
            } catch (Exception e) {
                return e;
            }
        },
        ok -> finish引导(),
        err -> {
            // **失败后必须把按钮恢复**，否则用户改完输入也没法重试
            // （代码审查抓出来的必崩项：这里原来写的是 setEnabled(false)）。
            btnSubmit.setEnabled(true);
            btnSubmit.setClickable(true);
            String msg = err instanceof com.moodtree.app.util.ApiClient.ApiException
                    ? err.getMessage() : (isLoginMode ? "登录失败：" : "注册失败：") + err.getMessage();
            tvStatus.setText(msg);
            // 错误也允许跳过（tvSkip 始终可见）
        });
    }

    // ---- 完成引导 ----

    private void finish引导() {
        app.config().setPhixSessionDone(true);
        goNext();
    }

    private void goNext() {
        Intent i = app.config().canEnterMain()
                ? new Intent(this, MainActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(i);
        finish();
    }

    private void applyTheme() {
        findViewById(android.R.id.content).setBackgroundColor(Theme.BG);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(Theme.BG);
            getWindow().getDecorView().setSystemUiVisibility(
                    Theme.isDarkTheme() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        // 标题
        tvTitle.setTextColor(Theme.INK);
        tvHint.setTextColor(Theme.INK_SOFT);
        tvStatus.setTextColor(Theme.INK_SOFT);
        tvSkip.setTextColor(Theme.ACCENT);

        // 按钮样式
        stylePrimaryBtn(btnHave);
        stylePrimaryBtn(btnNone);
        stylePrimaryBtn(btnSubmit);
        btnBack.setTextColor(Theme.INK_SOFT);
        btnBack.setBackgroundColor(Color.TRANSPARENT);

        // 输入框
        styleInput(etServer);
        styleInput(etUsername);
        styleInput(etPassword);
        styleInput(etPassword2);
    }

    private void stylePrimaryBtn(Button b) {
        b.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(10);
        gd.setColor(Theme.ACCENT);
        b.setBackground(gd);
    }

    private void styleInput(EditText et) {
        et.setTextColor(Theme.INK);
        et.setHintTextColor(Theme.INK_SOFT);
        et.setBackground(Theme.createInputBg());
    }
}
