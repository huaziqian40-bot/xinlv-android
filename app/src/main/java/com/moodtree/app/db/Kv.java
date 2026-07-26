package com.moodtree.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 键值对：存 last_sync（同步游标）、profile_cache、moods_cache 等。 */
@Entity(tableName = "kv")
public class Kv {
    @PrimaryKey
    @NonNull
    public String key;
    public String value;
}
