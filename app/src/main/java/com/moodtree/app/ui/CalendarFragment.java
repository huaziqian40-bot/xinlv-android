package com.moodtree.app.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Bg;
import com.moodtree.app.util.Dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 心情日历：月历网格（顶部固定，每格显示当天最多次数的心情 emoji）+ 底部当天心情记录列表（可滚动）。
 *  支持月/周/年三视图切换。
 *  写库走 MainActivity.saveMoodEntry/deleteMoodEntry（自动 dirty + 同步）。
 *  游客：本地照常记，同步静默跳过。 */
public class CalendarFragment extends BaseFragment implements Refreshable {

    private GridView grid;
    private CalendarAdapter adapter;
    private TextView tvMonth, tvSync, tvDayTitle;
    private DayEntriesAdapter dayAdapter;
    private YearMonth currentMonth;
    private LocalDate selectedDate;   // 当前展开的日期

    // 视图切换
    private View paneMonth, paneWeek, paneYear;
    private TextView btnViewMonth, btnViewWeek, btnViewYear;
    private LinearLayout weekGrid, yearGrid;
    private TextView tvWeekTitle, tvYearTitle;
    private LocalDate weekAnchor;   // 周视图锚点（周一）
    private int yearNum;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_calendar, container, false);
        themeBackground(root);

        tvMonth = root.findViewById(R.id.tvMonth);
        tvSync = root.findViewById(R.id.tvSync);
        grid = root.findViewById(R.id.gridCalendar);
        tvDayTitle = root.findViewById(R.id.tvDayTitle);

        adapter = new CalendarAdapter(inflater);
        grid.setAdapter(adapter);
        adapter.setClickListener(this::onCellClick);

        root.<TextView>findViewById(R.id.btnPrev).setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            reload();
        });
        root.<TextView>findViewById(R.id.btnNext).setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            reload();
        });
        root.<Button>findViewById(R.id.btnAddMood).setOnClickListener(v -> {
            String targetDate = selectedDate != null ? selectedDate.toString() : Dates.today();
            openNewMood(targetDate);
        });

        RecyclerView rvDay = root.findViewById(R.id.rvDayEntries);
        rvDay.setLayoutManager(new LinearLayoutManager(getContext()));
        dayAdapter = new DayEntriesAdapter(this::confirmDelete);
        rvDay.setAdapter(dayAdapter);

        // ===== 视图切换 =====
        paneMonth = root.findViewById(R.id.paneMonth);
        paneWeek = root.findViewById(R.id.paneWeek);
        paneYear = root.findViewById(R.id.paneYear);
        btnViewMonth = root.findViewById(R.id.btnViewMonth);
        btnViewWeek = root.findViewById(R.id.btnViewWeek);
        btnViewYear = root.findViewById(R.id.btnViewYear);
        weekGrid = root.findViewById(R.id.weekGrid);
        yearGrid = root.findViewById(R.id.yearGrid);
        tvWeekTitle = root.findViewById(R.id.tvWeekTitle);
        tvYearTitle = root.findViewById(R.id.tvYearTitle);

        btnViewMonth.setOnClickListener(v -> switchView("month"));
        btnViewWeek.setOnClickListener(v -> switchView("week"));
        btnViewYear.setOnClickListener(v -> switchView("year"));

        // 周导航
        root.<TextView>findViewById(R.id.btnWeekPrev).setOnClickListener(v -> {
            weekAnchor = weekAnchor.minusDays(7);
            renderWeek();
        });
        root.<TextView>findViewById(R.id.btnWeekNext).setOnClickListener(v -> {
            weekAnchor = weekAnchor.plusDays(7);
            renderWeek();
        });
        // 年导航
        root.<TextView>findViewById(R.id.btnYearPrev).setOnClickListener(v -> {
            yearNum--;
            renderYear();
        });
        root.<TextView>findViewById(R.id.btnYearNext).setOnClickListener(v -> {
            yearNum++;
            renderYear();
        });

        currentMonth = YearMonth.now();
        weekAnchor = LocalDate.now().with(DayOfWeek.MONDAY);
        yearNum = LocalDate.now().getYear();
        reload();
        // 默认显示今天的记录
        showDay(LocalDate.now());
        return root;
    }

    @Override
    public void refresh() { reload(); }

    /** 切换到月/周/年视图 */
    private void switchView(String view) {
        View showPane, hidePane;
        if (view.equals("month")) {
            showPane = paneMonth;
            hidePane = paneWeek.getVisibility() == View.VISIBLE ? paneWeek : paneYear;
        } else if (view.equals("week")) {
            showPane = paneWeek;
            hidePane = paneMonth.getVisibility() == View.VISIBLE ? paneMonth : paneYear;
        } else {
            showPane = paneYear;
            hidePane = paneMonth.getVisibility() == View.VISIBLE ? paneMonth : paneWeek;
        }

        // 隐藏旧视图：淡出
        Animation fadeOut = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out);
        hidePane.startAnimation(fadeOut);
        hidePane.setVisibility(View.GONE);

        // 显示新视图：淡入
        Animation fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in);
        showPane.startAnimation(fadeIn);
        showPane.setVisibility(View.VISIBLE);

        updateTabActive(view);
        if (view.equals("week")) renderWeek();
        if (view.equals("year")) renderYear();
    }

    private void updateTabActive(String active) {
        btnViewMonth.setTextColor(active.equals("month") ? Theme.ACCENT : Theme.INK_SOFT);
        btnViewWeek.setTextColor(active.equals("week") ? Theme.ACCENT : Theme.INK_SOFT);
        btnViewYear.setTextColor(active.equals("year") ? Theme.ACCENT : Theme.INK_SOFT);
    }

    /** 重新读库：渲染当前月网格（每格显示当天最多次的心情 emoji）+ 刷新底部列表 */
    public void reload() {
        if (currentMonth == null || !isAdded()) return;
        Bg.run(() -> {
            String prefix = Dates.monthPrefix(currentMonth);
            List<MoodEntry> monthEntries = app().db().moodDao().listForMonth(prefix);
            // 统计每日期最多的心情，显示在格子中
            // date -> {moodKey -> count}
            Map<String, Map<String, Integer>> countByDate = new HashMap<>();
            for (MoodEntry e : monthEntries) {
                Map<String, Integer> counts = countByDate.get(e.date);
                if (counts == null) {
                    counts = new HashMap<>();
                    countByDate.put(e.date, counts);
                }
                counts.put(e.mood, counts.getOrDefault(e.mood, 0) + 1);
            }
            Map<String, String> moodByDate = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : countByDate.entrySet()) {
                String bestMood = null;
                int maxCount = 0;
                for (Map.Entry<String, Integer> mc : entry.getValue().entrySet()) {
                    if (mc.getValue() > maxCount) {
                        maxCount = mc.getValue();
                        bestMood = mc.getKey();
                    }
                }
                if (bestMood != null) moodByDate.put(entry.getKey(), bestMood);
            }
            Bg.ui(() -> {
                if (!isAdded()) return;
                tvMonth.setText(Dates.monthLabel(currentMonth));
                adapter.setMonth(currentMonth, moodByDate);
                // 刷新底部列表（如果当前选中日期仍在当月）
                if (selectedDate != null && currentMonth.equals(YearMonth.from(selectedDate))) {
                    refreshDayEntries();
                }
            });
        });
    }

    /** 点格子：展开该日详情在底部（有记录看记录，无记录看空列表 + 底部按钮记心情） */
    private void onCellClick(CalendarAdapter.Cell cell) {
        if (cell == null) return;
        showDay(cell.date);
    }

    /** 展开某日详情到底部区域 */
    private void showDay(LocalDate date) {
        selectedDate = date;
        String iso = date.toString();
        tvDayTitle.setText(Dates.display(iso));
        // 更新添加按钮文案
        Button btnAdd = getView() == null ? null : getView().findViewById(R.id.btnAddMood);
        if (btnAdd != null) {
            boolean isToday = date.equals(java.time.LocalDate.now());
            btnAdd.setText(isToday ? "＋ 记今天的心情" : "＋ 记 " + Dates.display(iso) + " 的心情");
        }
        refreshDayEntries();
    }

    /** 刷新底部列表（当前 selectedDate） */
    private void refreshDayEntries() {
        if (selectedDate == null) return;
        String iso = selectedDate.toString();
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
                    ((MainActivity) requireActivity()).deleteMoodEntry(e, () -> {
                        showDay(java.time.LocalDate.parse(e.date));
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 周视图 ====================

    /** 渲染周视图：7 天横排，纵轴为时间（0 点在顶部），情绪按时刻纵向定位 */
    private void renderWeek() {
        if (!isAdded()) return;
        LocalDate monday = weekAnchor;
        LocalDate sunday = monday.plusDays(6);
        tvWeekTitle.setText(monday.getMonthValue() + "月" + monday.getDayOfMonth() + "日 ～ "
                + sunday.getMonthValue() + "月" + sunday.getDayOfMonth() + "日");

        String start = monday.toString();
        String end = sunday.toString();

        Bg.run(() -> {
            List<MoodEntry> entries = app().db().moodDao().listForRange(start, end);
            // 按日期分组
            Map<String, List<MoodEntry>> byDay = new HashMap<>();
            for (int i = 0; i < 7; i++) {
                byDay.put(monday.plusDays(i).toString(), new ArrayList<>());
            }
            for (MoodEntry e : entries) {
                List<MoodEntry> list = byDay.get(e.date);
                if (list != null) list.add(e);
            }

            final List<String> dayLabels = new ArrayList<>();
            dayLabels.add("一"); dayLabels.add("二"); dayLabels.add("三");
            dayLabels.add("四"); dayLabels.add("五"); dayLabels.add("六"); dayLabels.add("日");

            Bg.ui(() -> {
                if (!isAdded()) return;
                weekGrid.removeAllViews();

                // 第 0 列：时间轴（3 小时间隔）
                LinearLayout axisCol = new LinearLayout(requireContext());
                axisCol.setOrientation(LinearLayout.VERTICAL);
                axisCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.6f));

                // 日期头占位
                TextView axisHeader = new TextView(requireContext());
                axisHeader.setText("");
                axisHeader.setTextSize(10);
                axisHeader.setPadding(0, 0, 4, 0);
                axisCol.addView(axisHeader);

                // 时间刻度
                int totalHeight = 1440; // 一天 1440 分钟
                int[] hourMarks = {0, 3, 6, 9, 12, 15, 18, 21};
                for (int h : hourMarks) {
                    TextView hourLabel = new TextView(requireContext());
                    hourLabel.setText(h + ":00");
                    hourLabel.setTextSize(10);
                    hourLabel.setTextColor(Theme.INK_SOFT);
                    hourLabel.setPadding(0, 0, 4, 0);
                    // 用 marginTop 模拟纵向位置
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = (int) ((h / 24.0) * totalHeight * 0.9f);
                    hourLabel.setLayoutParams(lp);
                    axisCol.addView(hourLabel);
                }
                weekGrid.addView(axisCol);

                // 7 天列
                String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
                for (int i = 0; i < 7; i++) {
                    final int dayIndex = i;
                    LocalDate d = monday.plusDays(i);
                    String iso = d.toString();
                    List<MoodEntry> dayEntries = byDay.get(iso);

                    LinearLayout dayCol = new LinearLayout(requireContext());
                    dayCol.setOrientation(LinearLayout.VERTICAL);
                    dayCol.setLayoutParams(new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                    // 日期头
                    TextView header = new TextView(requireContext());
                    header.setText(weekDays[i] + "\n" + d.getDayOfMonth());
                    header.setTextSize(11);
                    header.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                    header.setTextColor(Theme.INK);
                    header.setPadding(0, 0, 0, 8);
                    dayCol.addView(header);

                    // 轨道容器
                    LinearLayout track = new LinearLayout(requireContext());
                    track.setOrientation(LinearLayout.VERTICAL);
                    track.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                    if (dayEntries != null && !dayEntries.isEmpty()) {
                        for (MoodEntry e : dayEntries) {
                            MoodMeta meta = MoodMeta.of(e.mood);
                            int minutes = Dates.minutesOfDay(e.at);
                            float topPct = (float) minutes / 1440f;

                            View dot = createWeekDot(meta.emoji, meta.color, topPct, iso);
                            track.addView(dot);
                        }
                    }

                    // 点击整列展开该日
                    dayCol.setOnClickListener(v -> showDay(d));
                    dayCol.addView(track);
                    weekGrid.addView(dayCol);
                }
            });
        });
    }

    /** 创建周视图中的一个情绪点（用 marginTop 定位） */
    private View createWeekDot(String emoji, String color, float topPct, String dateIso) {
        TextView dot = new TextView(requireContext());
        dot.setText(emoji);
        dot.setTextSize(16);
        dot.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (topPct * 400); // 按 400dp 轨道高度估算偏移
        if (lp.topMargin < 2) lp.topMargin = 2;
        dot.setLayoutParams(lp);
        return dot;
    }

    // ==================== 年视图 ====================

    /** 渲染年视图：12 个小月历，每天一个情绪颜色点 */
    private void renderYear() {
        if (!isAdded()) return;
        tvYearTitle.setText(yearNum + "年");

        String start = yearNum + "-01-01";
        String end = yearNum + "-12-31";

        Bg.run(() -> {
            List<MoodEntry> entries = app().db().moodDao().listForRange(start, end);
            // 取每天最多次数的心情
            Map<String, Map<String, Integer>> countByDate = new HashMap<>();
            for (MoodEntry e : entries) {
                Map<String, Integer> counts = countByDate.get(e.date);
                if (counts == null) {
                    counts = new HashMap<>();
                    countByDate.put(e.date, counts);
                }
                counts.put(e.mood, counts.getOrDefault(e.mood, 0) + 1);
            }
            Map<String, String> dayMood = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : countByDate.entrySet()) {
                String bestMood = null;
                int maxCount = 0;
                for (Map.Entry<String, Integer> mc : entry.getValue().entrySet()) {
                    if (mc.getValue() > maxCount) {
                        maxCount = mc.getValue();
                        bestMood = mc.getKey();
                    }
                }
                if (bestMood != null) dayMood.put(entry.getKey(), bestMood);
            }

            Bg.ui(() -> {
                if (!isAdded()) return;
                yearGrid.removeAllViews();

                // 3 列 × 4 行
                LinearLayout currentRow = null;
                int colInRow = 0;

                for (int m = 1; m <= 12; m++) {
                    if (colInRow == 0) {
                        currentRow = new LinearLayout(requireContext());
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        yearGrid.addView(currentRow);
                    }

                    View monthBlock = createMonthBlock(yearNum, m, dayMood);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    lp.setMargins(4, 8, 4, 8);
                    monthBlock.setLayoutParams(lp);
                    currentRow.addView(monthBlock);

                    colInRow++;
                    if (colInRow >= 3) colInRow = 0;
                }
            });
        });
    }

    /** 创建单个月份块：标题 + 7 列日期网格 */
    private View createMonthBlock(int year, int month, Map<String, String> dayMood) {
        LinearLayout block = new LinearLayout(requireContext());
        block.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Theme.CARD);
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), Theme.DIVIDER);
        block.setBackground(gd);
        int pad = dp(6);
        block.setPadding(pad, pad, pad, pad);

        // 月份标题
        TextView title = new TextView(requireContext());
        title.setText(month + "月");
        title.setTextSize(12);
        title.setTextColor(Theme.INK);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 4, 0, 4);
        block.addView(title);

        // 7 列网格
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        // 星期表头
        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
        for (String wd : weekDays) {
            TextView h = new TextView(requireContext());
            h.setText(wd);
            h.setTextSize(8);
            h.setTextColor(Theme.INK_SOFT);
            h.setGravity(android.view.Gravity.CENTER);
            h.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            headerRow.addView(h);
        }
        grid.addView(headerRow);

        // 日期格子
        LocalDate first = YearMonth.of(year, month).atDay(1);
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        // 周一=1...周日=7，前导空格数
        int leading = first.getDayOfWeek().getValue() % 7;

        LinearLayout row = null;
        int col = 0;

        // 前导空格
        if (leading > 0) {
            row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < leading; i++) {
                TextView empty = new TextView(requireContext());
                empty.setLayoutParams(new LinearLayout.LayoutParams(0, 24, 1f));
                row.addView(empty);
                col++;
            }
        }

        for (int d = 1; d <= daysInMonth; d++) {
            if (col == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
            }

            String iso = String.format("%04d-%02d-%02d", year, month, d);
            String moodKey = dayMood.get(iso);
            MoodMeta meta = moodKey != null ? MoodMeta.of(moodKey) : null;

            View cell = createYearCell(meta);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, 24, 1f));

            LocalDate date = LocalDate.of(year, month, d);
            cell.setOnClickListener(v -> showDay(date));

            row.addView(cell);
            col++;

            if (col >= 7) {
                grid.addView(row);
                row = null;
                col = 0;
            }
        }
        // 末尾补全
        if (row != null) {
            while (col < 7) {
                TextView empty = new TextView(requireContext());
                empty.setLayoutParams(new LinearLayout.LayoutParams(0, 24, 1f));
                row.addView(empty);
                col++;
            }
            grid.addView(row);
        }

        block.addView(grid);
        return block;
    }

    /** 年视图单个日期格子：有情绪时显示 emoji */
    private View createYearCell(MoodMeta meta) {
        if (meta == null) {
            TextView empty = new TextView(requireContext());
            empty.setLayoutParams(new LinearLayout.LayoutParams(0, 24, 1f));
            return empty;
        }
        TextView dot = new TextView(requireContext());
        dot.setText(meta.emoji);
        dot.setTextSize(10);
        dot.setGravity(android.view.Gravity.CENTER);
        return dot;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}