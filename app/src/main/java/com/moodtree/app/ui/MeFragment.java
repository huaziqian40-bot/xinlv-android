package com.moodtree.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.moodtree.app.R;

/** 我的：连胜/徽章/总记录 + 设置（主题/服务器地址/刷新目录）。游客显示本地统计。阶段五填充。 */
public class MeFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_stub, container, false);
        themeBackground(root);
        ((TextView) root.findViewById(R.id.tvStub)).setText("👤 我的\n（即将实现）");
        return root;
    }
}
