package com.moodtree.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moodtree.app.R;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Bg;

import java.util.ArrayList;
import java.util.List;

/** AI 树洞：气泡聊天，仅联网可用（AI 与危机硬拦截都在服务器，与网页版一致）。
 *  命中危机词时服务器返回 crisis:true，用醒目求助卡展示并附热线。
 *  游客 / 离线：禁用输入，显示引导横幅。 */
public class ChatFragment extends BaseFragment implements Refreshable {

    /** 一条消息：角色 + 文本 + 是否危机卡 */
    private static class Msg {
        String role;     // user / assistant
        String text;
        boolean crisis;
        Msg(String role, String text, boolean crisis) {
            this.role = role; this.text = text; this.crisis = crisis;
        }
    }

    private final List<Msg> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rv;
    private EditText etInput;
    private Button btnSend, btnClear;
    private TextView tvBanner;
    private boolean sending;
    private boolean historyLoaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);
        themeBackground(root);

        rv = root.findViewById(R.id.rvMessages);
        etInput = root.findViewById(R.id.etInput);
        btnSend = root.findViewById(R.id.btnSend);
        btnClear = root.findViewById(R.id.btnClear);
        tvBanner = root.findViewById(R.id.tvBanner);

        adapter = new ChatAdapter();
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        btnSend.setOnClickListener(v -> send());
        btnClear.setOnClickListener(v -> clearHistory());
        return root;
    }

    @Override
    public void refresh() {
        if (historyLoaded) return;
        historyLoaded = true;
        if (!app().config().loggedIn()) {
            // 游客：树洞在服务器上，必须登录
            showBanner("🌱 树洞要登录后才能陪你聊天。去「我的」页登录 / 注册，游客期间的记录不会丢。");
            setInputEnabled(false);
            return;
        }
        Bg.run(() -> {
                    if (!app().api().ping()) return null;      // 离线
                    return app().api().chatHistory();
                },
                resp -> {
                    if (resp == null) {
                        showBanner("🌧 现在不在线，树洞需要联网才能陪你聊。心情记录和推荐离线也能用。");
                        setInputEnabled(false);
                        return;
                    }
                    for (JsonElement el : resp.getAsJsonArray("messages")) {
                        JsonObject m = el.getAsJsonObject();
                        addBubble(m.get("role").getAsString(), m.get("content").getAsString(), false);
                    }
                    if (messages.isEmpty()) {
                        addBubble("assistant", "你好呀，我是你的树洞。开心或难过的事，都可以说给我听。", false);
                    }
                },
                err -> showBanner("聊天记录加载失败：" + err.getMessage()));
    }

    private void showBanner(String text) {
        tvBanner.setText(text);
        tvBanner.setVisibility(View.VISIBLE);
    }

    private void setInputEnabled(boolean enabled) {
        etInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
    }

    private void send() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty() || sending) return;
        sending = true;
        setInputEnabled(false);
        etInput.setText("");
        addBubble("user", text, false);
        int thinkingIdx = messages.size();
        addBubble("assistant", "…", false);

        Bg.run(() -> app().api().chat(text),
                resp -> {
                    sending = false;
                    setInputEnabled(true);
                    etInput.requestFocus();
                    messages.remove(thinkingIdx);
                    adapter.notifyItemRemoved(thinkingIdx);
                    boolean crisis = resp.has("crisis") && resp.get("crisis").getAsBoolean();
                    String reply = resp.get("reply").getAsString();
                    if (crisis && resp.has("hotline")) {
                        reply += "\n\n📞 心理援助热线：" + resp.get("hotline").getAsString();
                    }
                    addBubble("assistant", reply, crisis);
                },
                err -> {
                    sending = false;
                    setInputEnabled(true);
                    messages.remove(thinkingIdx);
                    adapter.notifyItemRemoved(thinkingIdx);
                    addBubble("assistant", "（发送失败：" + err.getMessage() + "）", false);
                });
    }

    private void addBubble(String role, String text, boolean crisis) {
        messages.add(new Msg(role, text, crisis));
        adapter.notifyItemInserted(messages.size() - 1);
        rv.scrollToPosition(messages.size() - 1);
    }

    private void clearHistory() {
        Bg.run(() -> { app().api().chatClear(); return null; },
                ok -> {
                    messages.clear();
                    adapter.notifyDataSetChanged();
                    addBubble("assistant", "对话已经清空啦。想说点什么新开始？", false);
                },
                err -> showBanner("清空失败：" + err.getMessage()));
    }

    // ---------- 气泡适配器 ----------

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_bubble, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Msg m = messages.get(position);
            h.bubble.setText(m.text);
            boolean isUser = "user".equals(m.role);

            // 圆角背景：左上/右上/右下 用 14dp，靠近自己一侧用 4dp（由 gravity 体现）
            float[] radii = new float[8];
            float big = dp(14), small = dp(4);
            if (isUser) {
                // 用户：右上/右下大圆角，左下小圆角 → 靠右
                radii[0] = big; radii[1] = big;     // 左上
                radii[2] = big; radii[3] = big;     // 右上
                radii[4] = small; radii[5] = small; // 右下
                radii[6] = big; radii[7] = big;     // 左下
                h.bubble.setTextColor(Color.WHITE);
                h.bubble.setBackground(tint(Theme.ACCENT, radii));
                h.row.setGravity(Gravity.END);
                // 靠右的气泡：留白在左侧，气泡贴右边缘（此前左右写反，气泡被顶离边缘 60dp）
                setSidePadding(h.row, dp(60), 0);
            } else if (m.crisis) {
                radii[0] = big; radii[1] = big;
                radii[2] = small; radii[3] = small;
                radii[4] = big; radii[5] = big;
                radii[6] = big; radii[7] = big;
                h.bubble.setTextColor(parseColor("#8a3b34"));
                h.bubble.setBackground(tint(parseColor("#fdeaea"), radii));
                h.bubble.setTypeface(h.bubble.getTypeface(), Typeface.BOLD);
                h.row.setGravity(Gravity.START);
                // 靠左的气泡：留白在右侧，气泡贴左边缘
                setSidePadding(h.row, 0, dp(60));
            } else {
                radii[0] = big; radii[1] = big;
                radii[2] = small; radii[3] = small;
                radii[4] = big; radii[5] = big;
                radii[6] = big; radii[7] = big;
                h.bubble.setTextColor(Theme.INK);
                h.bubble.setBackground(tint(Theme.CARD, radii));
                h.bubble.setTypeface(null, Typeface.NORMAL);
                h.row.setGravity(Gravity.START);
                setSidePadding(h.row, 0, dp(60));
            }

            // 新气泡淡入动画
            if (position == messages.size() - 1) {
                h.itemView.setAlpha(0f);
                h.itemView.animate().alpha(1f).setDuration(300).start();
            }
        }

        @Override public int getItemCount() { return messages.size(); }

        class VH extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView bubble;
            VH(@NonNull View v) {
                super(v);
                row = (LinearLayout) v;
                bubble = v.findViewById(R.id.tvBubble);
            }
        }
    }

    private android.graphics.drawable.Drawable tint(int color, float[] radii) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(radii);
        return d;
    }

    private void setSidePadding(LinearLayout row, int leftDp, int rightDp) {
        row.setPadding(leftDp, row.getPaddingTop(), rightDp, row.getPaddingBottom());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int parseColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }
}