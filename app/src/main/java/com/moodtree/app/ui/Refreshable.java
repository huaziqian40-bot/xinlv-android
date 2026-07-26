package com.moodtree.app.ui;

/** 可刷新数据的 Fragment 标记接口。切到该页或同步完成后由 MainActivity 调 refresh()。 */
public interface Refreshable {
    void refresh();
}
