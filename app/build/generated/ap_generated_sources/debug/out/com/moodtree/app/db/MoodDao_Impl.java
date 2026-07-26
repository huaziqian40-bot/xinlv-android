package com.moodtree.app.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MoodDao_Impl implements MoodDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MoodEntry> __insertionAdapterOfMoodEntry;

  public MoodDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMoodEntry = new EntityInsertionAdapter<MoodEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `mood_entry` (`uuid`,`date`,`at`,`mood`,`note`,`deleted`,`updatedAt`,`dirty`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final MoodEntry entity) {
        if (entity.uuid == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.uuid);
        }
        if (entity.date == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.date);
        }
        if (entity.at == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.at);
        }
        if (entity.mood == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.mood);
        }
        if (entity.note == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.note);
        }
        final int _tmp = entity.deleted ? 1 : 0;
        statement.bindLong(6, _tmp);
        if (entity.updatedAt == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.updatedAt);
        }
        final int _tmp_1 = entity.dirty ? 1 : 0;
        statement.bindLong(8, _tmp_1);
      }
    };
  }

  @Override
  public void upsert(final MoodEntry e) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMoodEntry.insert(e);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public MoodEntry get(final String uuid) {
    final String _sql = "SELECT * FROM mood_entry WHERE uuid = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (uuid == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, uuid);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
      final int _cursorIndexOfMood = CursorUtil.getColumnIndexOrThrow(_cursor, "mood");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
      final MoodEntry _result;
      if (_cursor.moveToFirst()) {
        _result = new MoodEntry();
        if (_cursor.isNull(_cursorIndexOfUuid)) {
          _result.uuid = null;
        } else {
          _result.uuid = _cursor.getString(_cursorIndexOfUuid);
        }
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _result.date = null;
        } else {
          _result.date = _cursor.getString(_cursorIndexOfDate);
        }
        if (_cursor.isNull(_cursorIndexOfAt)) {
          _result.at = null;
        } else {
          _result.at = _cursor.getString(_cursorIndexOfAt);
        }
        if (_cursor.isNull(_cursorIndexOfMood)) {
          _result.mood = null;
        } else {
          _result.mood = _cursor.getString(_cursorIndexOfMood);
        }
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _result.note = null;
        } else {
          _result.note = _cursor.getString(_cursorIndexOfNote);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDeleted);
        _result.deleted = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
          _result.updatedAt = null;
        } else {
          _result.updatedAt = _cursor.getString(_cursorIndexOfUpdatedAt);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
        _result.dirty = _tmp_1 != 0;
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<MoodEntry> listForDate(final String date) {
    final String _sql = "SELECT * FROM mood_entry WHERE date = ? AND deleted = 0 ORDER BY at";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (date == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, date);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
      final int _cursorIndexOfMood = CursorUtil.getColumnIndexOrThrow(_cursor, "mood");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
      final List<MoodEntry> _result = new ArrayList<MoodEntry>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MoodEntry _item;
        _item = new MoodEntry();
        if (_cursor.isNull(_cursorIndexOfUuid)) {
          _item.uuid = null;
        } else {
          _item.uuid = _cursor.getString(_cursorIndexOfUuid);
        }
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _item.date = null;
        } else {
          _item.date = _cursor.getString(_cursorIndexOfDate);
        }
        if (_cursor.isNull(_cursorIndexOfAt)) {
          _item.at = null;
        } else {
          _item.at = _cursor.getString(_cursorIndexOfAt);
        }
        if (_cursor.isNull(_cursorIndexOfMood)) {
          _item.mood = null;
        } else {
          _item.mood = _cursor.getString(_cursorIndexOfMood);
        }
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _item.note = null;
        } else {
          _item.note = _cursor.getString(_cursorIndexOfNote);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDeleted);
        _item.deleted = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
          _item.updatedAt = null;
        } else {
          _item.updatedAt = _cursor.getString(_cursorIndexOfUpdatedAt);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
        _item.dirty = _tmp_1 != 0;
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<MoodEntry> listForMonth(final String yearMonthPrefix) {
    final String _sql = "SELECT * FROM mood_entry WHERE date LIKE ? AND deleted = 0 ORDER BY date, at";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (yearMonthPrefix == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, yearMonthPrefix);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
      final int _cursorIndexOfMood = CursorUtil.getColumnIndexOrThrow(_cursor, "mood");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
      final List<MoodEntry> _result = new ArrayList<MoodEntry>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MoodEntry _item;
        _item = new MoodEntry();
        if (_cursor.isNull(_cursorIndexOfUuid)) {
          _item.uuid = null;
        } else {
          _item.uuid = _cursor.getString(_cursorIndexOfUuid);
        }
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _item.date = null;
        } else {
          _item.date = _cursor.getString(_cursorIndexOfDate);
        }
        if (_cursor.isNull(_cursorIndexOfAt)) {
          _item.at = null;
        } else {
          _item.at = _cursor.getString(_cursorIndexOfAt);
        }
        if (_cursor.isNull(_cursorIndexOfMood)) {
          _item.mood = null;
        } else {
          _item.mood = _cursor.getString(_cursorIndexOfMood);
        }
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _item.note = null;
        } else {
          _item.note = _cursor.getString(_cursorIndexOfNote);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDeleted);
        _item.deleted = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
          _item.updatedAt = null;
        } else {
          _item.updatedAt = _cursor.getString(_cursorIndexOfUpdatedAt);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
        _item.dirty = _tmp_1 != 0;
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<MoodEntry> listDirty() {
    final String _sql = "SELECT * FROM mood_entry WHERE dirty = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
      final int _cursorIndexOfMood = CursorUtil.getColumnIndexOrThrow(_cursor, "mood");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
      final List<MoodEntry> _result = new ArrayList<MoodEntry>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MoodEntry _item;
        _item = new MoodEntry();
        if (_cursor.isNull(_cursorIndexOfUuid)) {
          _item.uuid = null;
        } else {
          _item.uuid = _cursor.getString(_cursorIndexOfUuid);
        }
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _item.date = null;
        } else {
          _item.date = _cursor.getString(_cursorIndexOfDate);
        }
        if (_cursor.isNull(_cursorIndexOfAt)) {
          _item.at = null;
        } else {
          _item.at = _cursor.getString(_cursorIndexOfAt);
        }
        if (_cursor.isNull(_cursorIndexOfMood)) {
          _item.mood = null;
        } else {
          _item.mood = _cursor.getString(_cursorIndexOfMood);
        }
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _item.note = null;
        } else {
          _item.note = _cursor.getString(_cursorIndexOfNote);
        }
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDeleted);
        _item.deleted = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
          _item.updatedAt = null;
        } else {
          _item.updatedAt = _cursor.getString(_cursorIndexOfUpdatedAt);
        }
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
        _item.dirty = _tmp_1 != 0;
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countAlive() {
    final String _sql = "SELECT COUNT(*) FROM mood_entry WHERE deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<String> listDistinctDates() {
    final String _sql = "SELECT DISTINCT date FROM mood_entry WHERE deleted = 0 ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final List<String> _result = new ArrayList<String>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final String _item;
        if (_cursor.isNull(0)) {
          _item = null;
        } else {
          _item = _cursor.getString(0);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public void markClean(final List<String> uuids) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("UPDATE mood_entry SET dirty = 0 WHERE uuid IN (");
    final int _inputSize = uuids == null ? 1 : uuids.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    if (uuids == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : uuids) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
