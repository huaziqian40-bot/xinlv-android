package com.moodtree.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface KvDao {

    @Query("SELECT value FROM kv WHERE key = :key")
    String get(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void set(Kv kv);

    default void set(String key, String value) {
        Kv kv = new Kv();
        kv.key = key;
        kv.value = value;
        set(kv);
    }
}
