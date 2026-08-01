package com.moodtree.app.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Dates;

import java.util.UUID;

/** 记心情弹窗：选心情 + 强度滑动条 + 写备注，保存到本地（dirty 标记，由 SyncEngine 上传）。
 *  新建：date 默认今天；编辑：预填原记录。 */
public class MoodDialogFragment extends DialogFragment {

    public interface OnSaved { void onSaved(); }

    private static final String ARG_UUID = "uuid";
    private static final String ARG_DATE = "date";
    private static final String ARG_MOOD = "mood";
    private static final String ARG_NOTE = "note";
    private static final String ARG_AT = "at";
    private static final String ARG_INT_LEVEL = "intensityLevel";
    private static final String ARG_INT_PCT = "intensityPercent";

    private MoodPickAdapter moodAdapter;
    private OnSaved onSaved;
    private LinearLayout intensityRow;
    private SeekBar intensitySeek;
    private TextView tvIntensityText;
    private int selectedMoodColor = 0;

    /** 新建某天的心情记录 */
    public static MoodDialogFragment forNew(String date) {
        return build(null, date, null, null, null, 0, 0);
    }

    /** 编辑已有记录 */
    public static MoodDialogFragment forEdit(MoodEntry e) {
        return build(e.uuid, e.date, e.mood, e.note, e.at, e.intensityLevel, e.intensityPercent);
    }

    private static MoodDialogFragment build(String uuid, String date, String mood, String note, String at,
                                            int intensityLevel, int intensityPercent) {
        MoodDialogFragment f = new MoodDialogFragment();
        Bundle a = new Bundle();
        a.putString(ARG_UUID, uuid);
        a.putString(ARG_DATE, date);
        a.putString(ARG_MOOD, mood);
        a.putString(ARG_NOTE, note);
        a.putString(ARG_AT, at);
        a.putInt(ARG_INT_LEVEL, intensityLevel);
        a.putInt(ARG_INT_PCT, intensityPercent);
        f.setArguments(a);
        return f;
    }

    public void setOnSaved(OnSaved cb) { this.onSaved = cb; }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String uuid = args.getString(ARG_UUID);
        String date = args.getString(ARG_DATE);
        String mood = args.getString(ARG_MOOD);
        String note = args.getString(ARG_NOTE);
        String at = args.getString(ARG_AT);
        int initLevel = args.getInt(ARG_INT_LEVEL, 0);
        int initPct = args.getInt(ARG_INT_PCT, 50);
        boolean editing = uuid != null;

        View v = getLayoutInflater().inflate(R.layout.dialog_mood, null);
        v.setBackgroundColor(Theme.CARD);

        TextView tvTitle = v.findViewById(R.id.tvTitle);
        tvTitle.setText(editing ? "编辑心情" : "记 " + Dates.display(date) + " 的心情");
        tvTitle.setTextColor(Theme.INK);

        RecyclerView rv = v.findViewById(R.id.rvMoods);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 4));

        // 强度滑动条
        intensityRow = v.findViewById(R.id.intensityRow);
        intensitySeek = v.findViewById(R.id.intensitySeek);
        tvIntensityText = v.findViewById(R.id.tvIntensityText);

        // 强度标签颜色
        TextView tvIntensityLabel = v.findViewById(R.id.tvIntensityLabel);
        tvIntensityLabel.setTextColor(Theme.INK);
        tvIntensityText.setTextColor(Theme.INK_SOFT);

        // 选心情时显示滑动条并更新滑块颜色
        moodAdapter = new MoodPickAdapter(MoodMeta.all(), key -> {
            intensityRow.setVisibility(View.VISIBLE);
            MoodMeta m = MoodMeta.of(key);
            selectedMoodColor = parseColor(m.color);
            // 更新滑块颜色
            updateSeekbarColor(selectedMoodColor);
            // 用当前值触发一次标签更新
            onIntensityChange(intensitySeek.getProgress());
        });

        rv.setAdapter(moodAdapter);
        if (mood != null) moodAdapter.setSelected(mood);

        // 滑动条事件
        intensitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onIntensityChange(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 编辑时恢复强度值
        if (editing && initLevel > 0) {
            intensityRow.setVisibility(View.VISIBLE);
            intensitySeek.setProgress(initPct);
            if (mood != null) {
                selectedMoodColor = parseColor(MoodMeta.of(mood).color);
                updateSeekbarColor(selectedMoodColor);
            }
            onIntensityChange(initPct);
        }

        TextInputEditText etNote = v.findViewById(R.id.etNote);
        if (note != null) etNote.setText(note);
        etNote.setBackground(Theme.createInputBg());
        etNote.setTextColor(Theme.INK);
        etNote.setHintTextColor(Theme.INK_SOFT);

        Dialog d = new Dialog(requireContext());
        d.setContentView(v);

        Button btnCancel = v.findViewById(R.id.btnCancel);
        btnCancel.setTextColor(Theme.INK_SOFT);
        btnCancel.setOnClickListener(b -> dismiss());
        Button btnSave = v.findViewById(R.id.btnSave);
        btnSave.setBackground(Theme.createPrimaryButton());
        btnSave.setOnClickListener(b -> {
            String key = moodAdapter.selectedKey();
            if (key == null) {
                android.widget.Toast.makeText(getContext(), "先选一个心情吧", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            MoodEntry e = new MoodEntry();
            e.uuid = editing ? uuid : UUID.randomUUID().toString();
            e.date = date;
            e.at = editing && at != null ? at : Dates.nowIso();
            e.mood = key;
            e.note = etNote.getText() == null ? "" : etNote.getText().toString().trim();
            e.deleted = false;
            e.updatedAt = Dates.nowIso();
            e.dirty = true;
            e.intensityLevel = getLevelFromPercent(intensitySeek.getProgress());
            e.intensityPercent = intensitySeek.getProgress();
            // 传入回调：DB 写完后才触发 onSaved（确保 showDay 能看到新数据）
            ((MainActivity) requireActivity()).saveMoodEntry(e, () -> {
                if (onSaved != null) onSaved.onSaved();
                dismiss();
                // 记心情后自动跳转到推荐页，展示该心情的推荐内容
                ((MainActivity) requireActivity()).switchToRecommend(key);
            });
        });

        return d;
    }

    private void onIntensityChange(int pct) {
        int level = getLevelFromPercent(pct);
        String label;
        if (pct <= 20) label = getString(R.string.intensity_slight);
        else if (pct <= 45) label = getString(R.string.intensity_some);
        else if (pct <= 70) label = getString(R.string.intensity_quite);
        else label = getString(R.string.intensity_very);
        tvIntensityText.setText(label);
    }

    private static int getLevelFromPercent(int pct) {
        if (pct <= 20) return 1;
        else if (pct <= 45) return 2;
        else if (pct <= 70) return 3;
        else return 4;
    }

    private void updateSeekbarColor(int color) {
        // 滑块进度条颜色
        intensitySeek.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        // 高版本 SDK 移除了 android.R.drawable.seekbar_thumb，跳过 thumb 染色
    }

    private static int parseColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.GRAY; }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Dialog 默认窗口是 wrap_content 宽度，心情网格会被挤成竖排单字、按钮缩没。
        // 在 onStart 里把窗口拉宽到屏幕的 92%（onCreateDialog 阶段设置无效，会被系统布局覆盖）。
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            Window w = d.getWindow();
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
            w.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            // 弹窗进入动画：缩放淡入
            w.setWindowAnimations(android.R.style.Animation_Dialog);
            // 手动播放缩放淡入动画
            View content = w.getDecorView();
            Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.scale_in);
            content.startAnimation(anim);
        }
    }
}
