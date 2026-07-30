package com.moodtree.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** 心情记录 DAO。LWW / 墓碑 / dirty 语义与 Windows 端 LocalDb 对齐。 */
@Dao
public interface MoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MoodEntry e);

    @Query("SELECT * FROM mood_entry WHERE uuid = :uuid")
    MoodEntry get(String uuid);

    /** 某天的记录（不含墓碑），按时刻升序 */
    @Query("SELECT * FROM mood_entry WHERE date = :date AND deleted = 0 ORDER BY at")
    List<MoodEntry> listForDate(String date);

    /** 某月的记录（不含墓碑），日历渲染用。date LIKE 'yyyy-MM-%' */
    @Query("SELECT * FROM mood_entry WHERE date LIKE :yearMonthPrefix AND deleted = 0 ORDER BY date, at")
    List<MoodEntry> listForMonth(String yearMonthPrefix);

    /** 待上传的脏记录 */
    @Query("SELECT * FROM mood_entry WHERE dirty = 1")
    List<MoodEntry> listDirty();

    /** 上传成功后去掉脏标记 */
    @Query("UPDATE mood_entry SET dirty = 0 WHERE uuid IN (:uuids)")
    void markClean(List<String> uuids);

    @Query("SELECT COUNT(*) FROM mood_entry WHERE deleted = 0")
    int countAlive();

    /** 有记录的日期（去重，新→旧），游客模式本地算连胜用 */
    @Query("SELECT DISTINCT date FROM mood_entry WHERE deleted = 0 ORDER BY date DESC")
    List<String> listDistinctDates();

    /** 获取最新一条心情记录（非删除），用于情绪视觉叠色 */
    @Query("SELECT * FROM mood_entry WHERE deleted = 0 ORDER BY date DESC, at DESC LIMIT 1")
    MoodEntry getLatest();

    /** 某天范围内所有记录（不含墓碑），按日期+时刻排序。用于年/周视图本地渲染。 */
    @Query("SELECT * FROM mood_entry WHERE date >= :start AND date <= :end AND deleted = 0 ORDER BY date, at")
    List<MoodEntry> listForRange(String start, String end);
}
