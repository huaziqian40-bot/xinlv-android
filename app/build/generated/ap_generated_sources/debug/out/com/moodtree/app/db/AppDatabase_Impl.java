package com.moodtree.app.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile MoodDao _moodDao;

  private volatile KvDao _kvDao;

  private volatile CatalogDao _catalogDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `mood_entry` (`uuid` TEXT NOT NULL, `date` TEXT, `at` TEXT, `mood` TEXT, `note` TEXT, `deleted` INTEGER NOT NULL, `updatedAt` TEXT, `dirty` INTEGER NOT NULL, PRIMARY KEY(`uuid`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `kv` (`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog` (`kind` TEXT NOT NULL, `id` INTEGER NOT NULL, `payload` TEXT, PRIMARY KEY(`kind`, `id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e5126916cbcbcce77f3da891dbe14cd8')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `mood_entry`");
        db.execSQL("DROP TABLE IF EXISTS `kv`");
        db.execSQL("DROP TABLE IF EXISTS `catalog`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsMoodEntry = new HashMap<String, TableInfo.Column>(8);
        _columnsMoodEntry.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("date", new TableInfo.Column("date", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("at", new TableInfo.Column("at", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("mood", new TableInfo.Column("mood", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("note", new TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("updatedAt", new TableInfo.Column("updatedAt", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoodEntry.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMoodEntry = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMoodEntry = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMoodEntry = new TableInfo("mood_entry", _columnsMoodEntry, _foreignKeysMoodEntry, _indicesMoodEntry);
        final TableInfo _existingMoodEntry = TableInfo.read(db, "mood_entry");
        if (!_infoMoodEntry.equals(_existingMoodEntry)) {
          return new RoomOpenHelper.ValidationResult(false, "mood_entry(com.moodtree.app.db.MoodEntry).\n"
                  + " Expected:\n" + _infoMoodEntry + "\n"
                  + " Found:\n" + _existingMoodEntry);
        }
        final HashMap<String, TableInfo.Column> _columnsKv = new HashMap<String, TableInfo.Column>(2);
        _columnsKv.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKv.put("value", new TableInfo.Column("value", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKv = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKv = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKv = new TableInfo("kv", _columnsKv, _foreignKeysKv, _indicesKv);
        final TableInfo _existingKv = TableInfo.read(db, "kv");
        if (!_infoKv.equals(_existingKv)) {
          return new RoomOpenHelper.ValidationResult(false, "kv(com.moodtree.app.db.Kv).\n"
                  + " Expected:\n" + _infoKv + "\n"
                  + " Found:\n" + _existingKv);
        }
        final HashMap<String, TableInfo.Column> _columnsCatalog = new HashMap<String, TableInfo.Column>(3);
        _columnsCatalog.put("kind", new TableInfo.Column("kind", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCatalog.put("id", new TableInfo.Column("id", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCatalog.put("payload", new TableInfo.Column("payload", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCatalog = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCatalog = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCatalog = new TableInfo("catalog", _columnsCatalog, _foreignKeysCatalog, _indicesCatalog);
        final TableInfo _existingCatalog = TableInfo.read(db, "catalog");
        if (!_infoCatalog.equals(_existingCatalog)) {
          return new RoomOpenHelper.ValidationResult(false, "catalog(com.moodtree.app.db.CatalogItem).\n"
                  + " Expected:\n" + _infoCatalog + "\n"
                  + " Found:\n" + _existingCatalog);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "e5126916cbcbcce77f3da891dbe14cd8", "d9253058f8e517e508f5d6efa736ab08");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "mood_entry","kv","catalog");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `mood_entry`");
      _db.execSQL("DELETE FROM `kv`");
      _db.execSQL("DELETE FROM `catalog`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(MoodDao.class, MoodDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(KvDao.class, KvDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CatalogDao.class, CatalogDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public MoodDao moodDao() {
    if (_moodDao != null) {
      return _moodDao;
    } else {
      synchronized(this) {
        if(_moodDao == null) {
          _moodDao = new MoodDao_Impl(this);
        }
        return _moodDao;
      }
    }
  }

  @Override
  public KvDao kvDao() {
    if (_kvDao != null) {
      return _kvDao;
    } else {
      synchronized(this) {
        if(_kvDao == null) {
          _kvDao = new KvDao_Impl(this);
        }
        return _kvDao;
      }
    }
  }

  @Override
  public CatalogDao catalogDao() {
    if (_catalogDao != null) {
      return _catalogDao;
    } else {
      synchronized(this) {
        if(_catalogDao == null) {
          _catalogDao = new CatalogDao_Impl(this);
        }
        return _catalogDao;
      }
    }
  }
}
