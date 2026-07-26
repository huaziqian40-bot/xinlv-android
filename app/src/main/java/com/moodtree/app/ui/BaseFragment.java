package com.moodtree.app.ui;

import android.view.View;

import androidx.fragment.app.Fragment;

import com.moodtree.app.App;
import com.moodtree.app.model.Theme;

/** Fragment 基类：提供拿 App 的便捷方法，子类可见时刷新数据、应用主题背景。 */
public abstract class BaseFragment extends Fragment {

    protected App app() { return (App) requireActivity().getApplication(); }

    /** 应用主题背景色到根 View */
    protected void themeBackground(View root) {
        root.setBackgroundColor(Theme.BG);
    }
}
