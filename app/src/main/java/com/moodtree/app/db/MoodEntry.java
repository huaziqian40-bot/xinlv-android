package com.moodtree.app.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 心情记录实体。字段与服务端 + Windows 端一一对应：
 *  uuid 主键去重 / updated_at 最新者赢(LWW) / deleted 墓碑不真删 / dirty 标记待上传。
 *  date/at/updated_at 存 ISO8601 字符串（服务端就是这格式，直接存省转换）。 */
@Entity(tableName = "mood_entry")
public class MoodEntry {

    @PrimaryKey
    public String uuid;

    public String date;          // LocalDate 的 ISO 串，如 2026-07-26
    public String at;            // OffsetDateTime 的 ISO 串，可空
    public String mood;          // happy/calm/...（服务端 MOOD_KEYS）
    public String note;          // 备注，可空
    public boolean deleted;      // 墓碑标记
    public String updatedAt;     // LWW 比较用的时间戳
    public boolean dirty;        // 本地改动未上传标记
}
