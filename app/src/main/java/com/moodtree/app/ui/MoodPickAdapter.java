package com.moodtree.app.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.moodtree.app.R;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;

import java.util.List;

/** 记心情弹窗里 10 种心情选择网格。选中态高亮 + 边框，圆角卡片风格。 */
public class MoodPickAdapter extends RecyclerView.Adapter<MoodPickAdapter.VH> {

    public interface OnPick { void onPick(String moodKey); }

    private final List<MoodMeta> moods;
    private final OnPick picker;
    private int selected = -1;

    public MoodPickAdapter(List<MoodMeta> moods, OnPick picker) {
        this.moods = moods;
        this.picker = picker;
    }

    /** 预选某个心情（编辑场景） */
    public void setSelected(String key) {
        selected = -1;
        for (int i = 0; i < moods.size(); i++) {
            if (moods.get(i).key.equals(key)) { selected = i; break; }
        }
        notifyDataSetChanged();
    }

    public String selectedKey() {
        return selected >= 0 ? moods.get(selected).key : null;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mood_pick, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MoodMeta m = moods.get(position);
        h.emoji.setText(m.emoji);
        h.label.setText(m.label);
        boolean sel = position == selected;

        // 圆角背景：选中态用强调色 + 边框，未选中用 card 色 + 柔和边框
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(h.itemView, 12));
        if (sel) {
            bg.setColor(Theme.ACCENT);
            bg.setStroke(dp(h.itemView, 2), Theme.ACCENT);
            h.label.setTextColor(0xFFFFFFFF);
        } else {
            bg.setColor(Theme.CARD);
            bg.setStroke(dp(h.itemView, 1), Theme.DIVIDER);
            h.label.setTextColor(Theme.INK);
        }
        h.itemView.setBackground(bg);

        h.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = h.getAdapterPosition();
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(selected);
            if (picker != null) picker.onPick(moods.get(selected).key);
        });
    }

    @Override public int getItemCount() { return moods.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, label;
        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.tvEmoji);
            label = v.findViewById(R.id.tvLabel);
        }
    }

    private static int dp(View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density + 0.5f);
    }
}