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

/** 某日心情记录列表适配器。长按一条弹删除确认（走墓碑 + dirty）。 */
public class DayEntriesAdapter extends RecyclerView.Adapter<DayEntriesAdapter.VH> {

    public interface OnEntryLongClick { void onLongClick(MoodEntry e); }

    private final List<MoodEntry> items = new ArrayList<>();
    private final OnEntryLongClick longClick;

    public DayEntriesAdapter(OnEntryLongClick longClick) {
        this.longClick = longClick;
    }

    public void set(List<MoodEntry> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_day_entry, parent, false);
        return new VH(v);
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
