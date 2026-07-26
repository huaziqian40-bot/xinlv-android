package com.moodtree.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Bg;
import com.moodtree.app.util.Dates;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 心情日历：月历网格（每格显示当日心情 emoji）+ 某日详情列表 + 记心情弹窗。
 *  写库走 MainActivity.saveMoodEntry/deleteMoodEntry（自动 dirty + 同步）。
 *  游客：本地照常记，同步静默跳过。 */
public class CalendarFragment extends BaseFragment implements Refreshable {

    private GridView grid;
    private CalendarAdapter adapter;
    private TextView tvMonth, tvSync;
    private View dayPanel;
    private TextView tvDayTitle;
    private DayEntriesAdapter dayAdapter;
    private YearMonth currentMonth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_calendar, container, false);
        themeBackground(root);

        tvMonth = root.findViewById(R.id.tvMonth);
        tvSync = root.findViewById(R.id.tvSync);
        grid = root.findViewById(R.id.gridCalendar);
        dayPanel = root.findViewById(R.id.dayPanel);
        tvDayTitle = root.findViewById(R.id.tvDayTitle);

        adapter = new CalendarAdapter(inflater);
        grid.setAdapter(adapter);
        adapter.setClickListener(this::onCellClick);

        root.<Button>findViewById(R.id.btnPrev).setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            reload();
        });
        root.<Button>findViewById(R.id.btnNext).setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            reload();
        });
        root.<Button>findViewById(R.id.btnCloseDay).setOnClickListener(v -> {
            dayPanel.setVisibility(View.GONE);
        });
        root.<Button>findViewById(R.id.btnAddMood).setOnClickListener(v -> openNewMood(Dates.today()));

        RecyclerView rvDay = root.findViewById(R.id.rvDayEntries);
        rvDay.setLayoutManager(new LinearLayoutManager(getContext()));
        dayAdapter = new DayEntriesAdapter(this::confirmDelete);
        rvDay.setAdapter(dayAdapter);

        currentMonth = YearMonth.now();
        reload();
        return root;
    }

    @Override
    public void refresh() { reload(); }

    /** 重新读库：渲染当前月网格 + 若有选中日则刷新该日列表 */
    public void reload() {
        if (currentMonth == null || !isAdded()) return;
        Bg.run(() -> {
            String prefix = Dates.monthPrefix(currentMonth);
            List<MoodEntry> monthEntries = app().db().moodDao().listForMonth(prefix);
            Map<String, String> moodByDate = new HashMap<>();
            for (MoodEntry e : monthEntries) {
                // 一天可能多条，取第一条作为格子展示
                if (!moodByDate.containsKey(e.date)) moodByDate.put(e.date, e.mood);
            }
            Bg.ui(() -> {
                if (!isAdded()) return;
                tvMonth.setText(Dates.monthLabel(currentMonth));
                adapter.setMonth(currentMonth, moodByDate);
            });
        });
    }

    /** 点格子：有记录→展开该日详情；空日子→记心情 */
    private void onCellClick(CalendarAdapter.Cell cell) {
        if (cell == null) return;
        if (cell.moodKey != null) {
            showDay(cell.date);
        } else {
            openNewMood(cell.date.toString());
        }
    }

    /** 展开某日详情 */
    private void showDay(LocalDate date) {
        String iso = date.toString();
        tvDayTitle.setText(Dates.display(iso));
        dayPanel.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<MoodEntry> list = app().db().moodDao().listForDate(iso);
            Bg.ui(() -> { if (isAdded()) dayAdapter.set(list); });
        });
    }

    private void openNewMood(String date) {
        MoodDialogFragment d = MoodDialogFragment.forNew(date);
        d.setOnSaved(() -> showDay(java.time.LocalDate.parse(date)));
        d.show(getParentFragmentManager(), "mood");
    }

    /** 长按一条 → 确认删除（墓碑） */
    private void confirmDelete(MoodEntry e) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除这条记录？")
                .setMessage("删除后会同步到云端。")
                .setPositiveButton("删除", (d, w) -> {
                    ((MainActivity) requireActivity()).deleteMoodEntry(e);
                    // 刷新该日列表
                    showDay(java.time.LocalDate.parse(e.date));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 供外部更新同步状态条 */
    public void setSyncText(String text) {
        if (text == null || text.isEmpty()) {
            tvSync.setVisibility(View.GONE);
        } else {
            tvSync.setText(text);
            tvSync.setVisibility(View.VISIBLE);
        }
    }
}
