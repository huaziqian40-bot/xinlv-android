package com.moodtree.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.moodtree.app.R;

/** 今日推荐：选心情 → 音乐/小行动/小知识/视频。阶段四填充。 */
public class RecommendFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_stub, container, false);
        themeBackground(root);
        ((TextView) root.findViewById(R.id.tvStub)).setText("🎵 今日推荐\n（即将实现）");
        return root;
    }
}
