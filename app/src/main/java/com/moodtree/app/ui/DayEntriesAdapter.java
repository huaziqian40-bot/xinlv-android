package com.moodtree.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Dates;

import java.util.ArrayList;
import java.util.List;

/** 强度百分位 → 中文标签 */
class IntensityText {
    static String of(int level, int pct) {
        String[] labels = {"", "略微", "有点", "相当", "十分"};
        String l = level >= 1 && level <= 4 ? labels[level] : "";
        if (pct > 0) return l + " · " + pct + "%";
        return l;
    }
}

/** 某日心情记录列表适配器。长按一条弹删除确认（走墓碑 + dirty）。
 *  带 fade-in 动画。 */
public class DayEntriesAdapter extends RecyclerView.Adapter<DayEntriesAdapter.VH> {

    public interface OnEntryLongClick { void onLongClick(MoodEntry e); }

    private final List<MoodEntry> items = new ArrayList<>();
    private final OnEntryLongClick longClick;
    private int lastAnimated = -1;

    public DayEntriesAdapter(OnEntryLongClick longClick) {
        this.longClick = longClick;
    }

    public void set(List<MoodEntry> list) {
        items.clear();
        items.addAll(list);
        lastAnimated = -1;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_day_entry, parent, false);
        VH h = new VH(v);
        // 主题：item 背景、文字颜色、emoji 底色
        v.setBackground(Theme.createCardBg(
                v.getContext().getResources().getDisplayMetrics().density, 10));
        h.emoji.setBackground(Theme.createEmojiBg());
        h.time.setBackground(Theme.createInputBg());
        h.time.setTextColor(Theme.INK_SOFT);
        h.mood.setTextColor(Theme.INK);
        h.intensity.setTextColor(Theme.INK_SOFT);
        h.note.setTextColor(Theme.INK);
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MoodEntry e = items.get(position);
        MoodMeta m = MoodMeta.of(e.mood);
        h.emoji.setText(m.emoji);
        h.mood.setText(m.label);
        String t = Dates.timeOfDay(e.at);
        h.time.setText(t.isEmpty() ? "" : t);
        // 强度标签
        String intensityLabel = IntensityText.of(e.intensityLevel, e.intensityPercent);
        if (!intensityLabel.isEmpty()) {
            h.intensity.setText(intensityLabel);
            h.intensity.setVisibility(View.VISIBLE);
        } else {
            h.intensity.setVisibility(View.GONE);
        }
        if (e.note != null && !e.note.isEmpty()) {
            h.note.setText(e.note);
            h.note.setVisibility(View.VISIBLE);
        } else {
            h.note.setVisibility(View.GONE);
        }
        h.itemView.setOnLongClickListener(v -> {
            if (longClick != null) longClick.onLongClick(e);
            return true;
        });

        // 动画：新出现的 item 执行淡入 + 上滑
        if (position > lastAnimated) {
            float density = h.itemView.getContext().getResources().getDisplayMetrics().density;
            h.itemView.setAlpha(0f);
            h.itemView.setTranslationY(20 * density);
            h.itemView.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(300)
                    .setStartDelay(position * 80)
                    .start();
            lastAnimated = position;
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, mood, note, time, intensity;
        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.tvEmoji);
            mood = v.findViewById(R.id.tvMood);
            note = v.findViewById(R.id.tvNote);
            time = v.findViewById(R.id.tvTime);
            intensity = v.findViewById(R.id.tvIntensity);
        }
    }
}