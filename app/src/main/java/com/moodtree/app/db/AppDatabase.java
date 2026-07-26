package com.moodtree.app.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** Room 数据库单例。三张表 mood_entry / kv / catalog，与 Windows 端 SQLite 结构对齐。 */
@Database(entities = {MoodEntry.class, Kv.class, CatalogItem.class},
          version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract MoodDao moodDao();
    public abstract KvDao kvDao();
    public abstract CatalogDao catalogDao();

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(), AppDatabase.class, "moodtree.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
