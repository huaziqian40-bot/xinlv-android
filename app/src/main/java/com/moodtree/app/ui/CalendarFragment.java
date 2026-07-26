package com.moodtree.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.moodtree.app.R;

/** 心情日历：月历网格 + 某日详情 + 记心情弹窗。阶段三填充。 */
public class CalendarFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_stub, container, false);
        themeBackground(root);
        ((TextView) root.findViewById(R.id.tvStub)).setText("📅 心情日历\n（即将实现）");
        return root;
    }
}
