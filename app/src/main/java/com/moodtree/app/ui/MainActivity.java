package com.moodtree.app.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.moodtree.app.App;
import com.moodtree.app.R;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.sync.SyncEngine;
import com.moodtree.app.util.Bg;

/** 主界面：底部导航四页（日历/推荐/树洞/我的）。游客与登录用户共用，各页内部按状态适配。
 *  本类还承担本地写库 + 触发同步的入口（saveMoodEntry / deleteMoodEntry / requestSync）。 */
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
        // 顶部避让状态栏（只给内容容器加顶部 padding，底部导航栏仍贴系统底）
        com.moodtree.app.util.Insets.applyTop(findViewById(R.id.fragmentContainer));

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
            // 切到某页时让它刷新数据（联网后可能已变化）
            if (target instanceof Refreshable) ((Refreshable) target).refresh();
            return true;
        });

        // 进主界面后异步同步一轮（登录用户）；游客静默跳过
        requestSync(null);
    }

    /** 保存（新建或编辑）一条心情记录到本地库，打 dirty 标记，再异步同步。 */
    public void saveMoodEntry(MoodEntry e) {
        Bg.run(() -> {
            app().db().moodDao().upsert(e);
            Bg.ui(this::refreshCalendar);
            requestSync(null);
        });
    }

    /** 删除一条记录（墓碑：deleted=1 + dirty=1），再异步同步。 */
    public void deleteMoodEntry(MoodEntry e) {
        Bg.run(() -> {
            e.deleted = true;
            e.updatedAt = com.moodtree.app.util.Dates.nowIso();
            e.dirty = true;
            app().db().moodDao().upsert(e);
            Bg.ui(this::refreshCalendar);
            requestSync(null);
        });
    }

    /** 触发一轮同步。游客/未登录跳过；结果可选回调到主线程。 */
    public void requestSync(SyncEngine.SyncResult.Callback cb) {
        App a = app();
        if (!a.config().loggedIn()) return;   // 游客不同步
        Bg.run(() -> {
            SyncEngine.SyncResult r = new SyncEngine(a.config(), a.api(), a.db()).sync();
            Bg.ui(() -> {
                refreshCalendar();
                if (cb != null) cb.onResult(r);
            });
        });
    }

    /** 让日历页重新读库刷新（写库/同步后调用） */
    private void refreshCalendar() {
        if (calendarFrag.isAdded()) calendarFrag.reload();
    }

    /** 给 Fragment 拿 App 的便捷方法 */
    public App app() { return (App) getApplication(); }
}
