package com.moodtree.app.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.moodtree.app.R;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.model.Theme;
import com.moodtree.app.util.Config;
import com.moodtree.app.util.Dates;
import com.moodtree.app.util.ImageLoader;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Color;
import android.widget.ImageView;

/** 日历网格适配器：周一为一周起点，前导空格补齐。每个格子：日期数字右上角 + 心情 emoji 居中。
 *  点格子始终展开该日详情。 */
public class CalendarAdapter extends BaseAdapter {

    /** 格子内容：null 表示占位空格（月初对齐用） */
    public static class Cell {
        public LocalDate date;
        public boolean isToday;
        public String moodKey;   // 该日第一条心情（空表示无记录）
    }

    public interface OnCellClick { void onClick(Cell cell); }

    private final LayoutInflater inflater;
    private Cell[] cells = new Cell[0];   // 动态大小：leading + daysInMonth
    private int actualCount;              // 实际格子数（含前导空格）
    private final Map<String, String> dateToMood = new HashMap<>();  // yyyy-MM-dd -> moodKey
    private OnCellClick clickListener;
    private YearMonth month;
    private LocalDate selectedDate;
    private String serverBase;

    public CalendarAdapter(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    /** 设置当前月份并重算网格（周一为起点），动态计算实际行数 */
    public void setMonth(YearMonth ym, Map<String, String> moodByDate) {
        this.month = ym;
        this.dateToMood.clear();
        if (moodByDate != null) this.dateToMood.putAll(moodByDate);

        LocalDate first = ym.atDay(1);
        // 周一=1...周日=7，转成"前导空格数"：让 1 号落在对应列
        // 周一(1)→0个前导空格(落在第0列)，周二(2)→1个...周日(7)→6个
        int leading = (first.getDayOfWeek().getValue() + 6) % 7;
        int daysInMonth = ym.lengthOfMonth();

        // 计算实际需要的格子数：leading + daysInMonth，向上取整到 7 的倍数
        int total = leading + daysInMonth;
        int rows = (total + 6) / 7;     // 实际行数（4~6）
        actualCount = rows * 7;         // 实际格子数
        cells = new Cell[actualCount];

        LocalDate today = LocalDate.now();

        for (int i = 0; i < actualCount; i++) {
            int dayNum = i - leading + 1;
            if (dayNum < 1 || dayNum > daysInMonth) {
                cells[i] = null;
            } else {
                Cell c = new Cell();
                c.date = ym.atDay(dayNum);
                c.isToday = c.date.equals(today);
                c.moodKey = dateToMood.get(c.date.toString());
                cells[i] = c;
            }
        }
        notifyDataSetChanged();
    }

    public YearMonth getMonth() { return month; }

    /** 返回实际行数（4~6），根据当前月动态计算 */
    public int getRowCount() { return actualCount / 7; }

    public void setClickListener(OnCellClick l) { this.clickListener = l; }

    /** 设置当前选中的日期，刷新高亮 */
    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return actualCount; }
    @Override public Object getItem(int position) { return cells[position]; }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_calendar_day, parent, false);
        }
        Cell c = cells[position];
        TextView tvDay = convertView.findViewById(R.id.tvDay);
        ImageView tvDot = convertView.findViewById(R.id.tvDot);

        if (c == null) {
            // 占位空格：透明，不响应点击
            tvDay.setText("");
            tvDot.setVisibility(View.GONE);
            convertView.setEnabled(false);
            convertView.setClickable(false);
            convertView.setBackgroundColor(0x00000000);
            return convertView;
        }

        tvDay.setText(String.valueOf(c.date.getDayOfMonth()));
        tvDay.setTextColor(c.isToday ? Color.WHITE : (c.date.equals(selectedDate) ? Theme.ACCENT : Theme.INK));

        if (c.moodKey != null) {
            MoodMeta m = MoodMeta.of(c.moodKey);
            if (serverBase == null) {
                serverBase = new Config(inflater.getContext()).serverBase();
            }
            ImageLoader.load(tvDot, serverBase + "/static/" + m.image);
            tvDot.setVisibility(View.VISIBLE);
        } else {
            tvDot.setVisibility(View.GONE);
        }

        // 选中/今天背景
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(6));

        boolean isSelected = c.date.equals(selectedDate);

        if (c.isToday) {
            // 今天：整格填充按钮颜色
            gd.setColor(Theme.ACCENT);
            gd.setStroke(0, 0x00000000);
        } else if (isSelected) {
            // 选中但不是今天：边框为按钮颜色
            gd.setColor(Theme.CARD);
            gd.setStroke(dp(2), Theme.ACCENT);
        } else {
            gd.setColor(Theme.CARD);
            gd.setStroke(dp(1), Theme.DIVIDER);
        }
        convertView.setBackground(gd);
        convertView.setEnabled(true);
        convertView.setClickable(true);
        convertView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(c);
        });
        return convertView;
    }

    private int dp(int v) {
        return (int) (v * inflater.getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
