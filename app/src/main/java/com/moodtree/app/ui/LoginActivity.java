package com.moodtree.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
        if (active) {
            b.setBackgroundColor(Theme.ACCENT);
            b.setTextColor(0xFFFFFFFF);
        } else {
            b.setBackgroundColor(0x00000000);
            b.setTextColor(Theme.INK_SOFT);
        }
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
    }

    @Override
    public void onBackPressed() {
        // 登录页不允许返回退出（避免误触关 App），与启动页语义一致
    }
}
