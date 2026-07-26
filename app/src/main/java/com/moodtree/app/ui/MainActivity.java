package com.moodtree.app.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.moodtree.app.App;
import com.moodtree.app.R;

/** 主界面：底部导航四页（日历/推荐/树洞/我的）。游客与登录用户共用，各页内部按状态适配。 */
public class MainActivity extends AppCompatActivity {

    private final CalendarFragment calendarFrag = new CalendarFragment();
    private final RecommendFragment recommendFrag = new RecommendFragment();
    private final ChatFragment chatFrag = new ChatFragment();
    private final MeFragment meFrag = new MeFragment();
    private Fragment active = calendarFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 没登录也不是游客 → 回登录页（防御性，正常不会走到）
        if (!((App) getApplication()).config().canEnterMain()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        // 一次性把四个 Fragment 都加进来，切换时只 show/hide（保留各自状态）
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, meFrag, "me").hide(meFrag)
                .add(R.id.fragmentContainer, chatFrag, "chat").hide(chatFrag)
                .add(R.id.fragmentContainer, recommendFrag, "recommend").hide(recommendFrag)
                .add(R.id.fragmentContainer, calendarFrag, "calendar")
                .commit();

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_calendar);
        nav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();
            if (id == R.id.nav_recommend) target = recommendFrag;
            else if (id == R.id.nav_chat) target = chatFrag;
            else if (id == R.id.nav_me) target = meFrag;
            else target = calendarFrag;
            if (target == active) return true;
            getSupportFragmentManager().beginTransaction()
                    .hide(active).show(target).commit();
            active = target;
            return true;
        });
    }

    /** 给 Fragment 拿 App 的便捷方法 */
    public App app() { return (App) getApplication(); }
}
