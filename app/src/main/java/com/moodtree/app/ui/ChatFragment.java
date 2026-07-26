package com.moodtree.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.moodtree.app.R;

/** AI 树洞：气泡聊天，命中危机词显示求助卡。游客/离线禁用。阶段四填充。 */
public class ChatFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_stub, container, false);
        themeBackground(root);
        ((TextView) root.findViewById(R.id.tvStub)).setText("💬 AI 树洞\n（即将实现）");
        return root;
    }
}
