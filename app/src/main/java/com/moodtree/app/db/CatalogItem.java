package com.moodtree.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 推荐目录缓存条目：songs/activities/tips/videos 各按 kind 分组，payload 存整条 JSON。 */
@Entity(tableName = "catalog", primaryKeys = {"kind", "id"})
public class CatalogItem {
    @NonNull
    public String kind;       // songs / activities / tips / videos
    public int id;
    public String payload;    // 该条目的完整 JSON
}
