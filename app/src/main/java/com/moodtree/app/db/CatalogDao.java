package com.moodtree.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CatalogItem item);

    @Query("DELETE FROM catalog WHERE kind = :kind")
    void clear(String kind);

    @Query("SELECT payload FROM catalog WHERE kind = :kind")
    List<String> all(String kind);
}
