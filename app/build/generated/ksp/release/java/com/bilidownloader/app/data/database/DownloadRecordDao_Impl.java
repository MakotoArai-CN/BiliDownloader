package com.bilidownloader.app.data.database;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.bilidownloader.app.data.model.DownloadRecord;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DownloadRecordDao_Impl implements DownloadRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DownloadRecord> __insertionAdapterOfDownloadRecord;

  private final EntityDeletionOrUpdateAdapter<DownloadRecord> __deletionAdapterOfDownloadRecord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public DownloadRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDownloadRecord = new EntityInsertionAdapter<DownloadRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `download_records` (`id`,`videoId`,`cid`,`title`,`quality`,`downloadPath`,`fileName`,`downloadTime`,`fileSize`,`mode`,`hasCover`,`hasDanmaku`,`hasSubtitle`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getVideoId());
        statement.bindLong(3, entity.getCid());
        statement.bindString(4, entity.getTitle());
        statement.bindLong(5, entity.getQuality());
        statement.bindString(6, entity.getDownloadPath());
        statement.bindString(7, entity.getFileName());
        statement.bindLong(8, entity.getDownloadTime());
        statement.bindLong(9, entity.getFileSize());
        statement.bindString(10, entity.getMode());
        final int _tmp = entity.getHasCover() ? 1 : 0;
        statement.bindLong(11, _tmp);
        final int _tmp_1 = entity.getHasDanmaku() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        final int _tmp_2 = entity.getHasSubtitle() ? 1 : 0;
        statement.bindLong(13, _tmp_2);
      }
    };
    this.__deletionAdapterOfDownloadRecord = new EntityDeletionOrUpdateAdapter<DownloadRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `download_records` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadRecord entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM download_records";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final DownloadRecord record, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDownloadRecord.insert(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final DownloadRecord record, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfDownloadRecord.handle(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DownloadRecord>> getAllRecords() {
    final String _sql = "SELECT * FROM download_records ORDER BY downloadTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"download_records"}, new Callable<List<DownloadRecord>>() {
      @Override
      @NonNull
      public List<DownloadRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVideoId = CursorUtil.getColumnIndexOrThrow(_cursor, "videoId");
          final int _cursorIndexOfCid = CursorUtil.getColumnIndexOrThrow(_cursor, "cid");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfQuality = CursorUtil.getColumnIndexOrThrow(_cursor, "quality");
          final int _cursorIndexOfDownloadPath = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadPath");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDownloadTime = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadTime");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfHasCover = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCover");
          final int _cursorIndexOfHasDanmaku = CursorUtil.getColumnIndexOrThrow(_cursor, "hasDanmaku");
          final int _cursorIndexOfHasSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "hasSubtitle");
          final List<DownloadRecord> _result = new ArrayList<DownloadRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpVideoId;
            _tmpVideoId = _cursor.getString(_cursorIndexOfVideoId);
            final long _tmpCid;
            _tmpCid = _cursor.getLong(_cursorIndexOfCid);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpQuality;
            _tmpQuality = _cursor.getInt(_cursorIndexOfQuality);
            final String _tmpDownloadPath;
            _tmpDownloadPath = _cursor.getString(_cursorIndexOfDownloadPath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpDownloadTime;
            _tmpDownloadTime = _cursor.getLong(_cursorIndexOfDownloadTime);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpMode;
            _tmpMode = _cursor.getString(_cursorIndexOfMode);
            final boolean _tmpHasCover;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasCover);
            _tmpHasCover = _tmp != 0;
            final boolean _tmpHasDanmaku;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfHasDanmaku);
            _tmpHasDanmaku = _tmp_1 != 0;
            final boolean _tmpHasSubtitle;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfHasSubtitle);
            _tmpHasSubtitle = _tmp_2 != 0;
            _item = new DownloadRecord(_tmpId,_tmpVideoId,_tmpCid,_tmpTitle,_tmpQuality,_tmpDownloadPath,_tmpFileName,_tmpDownloadTime,_tmpFileSize,_tmpMode,_tmpHasCover,_tmpHasDanmaku,_tmpHasSubtitle);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getRecord(final String videoId, final long cid, final int quality,
      final Continuation<? super DownloadRecord> $completion) {
    final String _sql = "SELECT * FROM download_records WHERE videoId = ? AND cid = ? AND quality = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, videoId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, cid);
    _argIndex = 3;
    _statement.bindLong(_argIndex, quality);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DownloadRecord>() {
      @Override
      @Nullable
      public DownloadRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVideoId = CursorUtil.getColumnIndexOrThrow(_cursor, "videoId");
          final int _cursorIndexOfCid = CursorUtil.getColumnIndexOrThrow(_cursor, "cid");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfQuality = CursorUtil.getColumnIndexOrThrow(_cursor, "quality");
          final int _cursorIndexOfDownloadPath = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadPath");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDownloadTime = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadTime");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfHasCover = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCover");
          final int _cursorIndexOfHasDanmaku = CursorUtil.getColumnIndexOrThrow(_cursor, "hasDanmaku");
          final int _cursorIndexOfHasSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "hasSubtitle");
          final DownloadRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpVideoId;
            _tmpVideoId = _cursor.getString(_cursorIndexOfVideoId);
            final long _tmpCid;
            _tmpCid = _cursor.getLong(_cursorIndexOfCid);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpQuality;
            _tmpQuality = _cursor.getInt(_cursorIndexOfQuality);
            final String _tmpDownloadPath;
            _tmpDownloadPath = _cursor.getString(_cursorIndexOfDownloadPath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpDownloadTime;
            _tmpDownloadTime = _cursor.getLong(_cursorIndexOfDownloadTime);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpMode;
            _tmpMode = _cursor.getString(_cursorIndexOfMode);
            final boolean _tmpHasCover;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasCover);
            _tmpHasCover = _tmp != 0;
            final boolean _tmpHasDanmaku;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfHasDanmaku);
            _tmpHasDanmaku = _tmp_1 != 0;
            final boolean _tmpHasSubtitle;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfHasSubtitle);
            _tmpHasSubtitle = _tmp_2 != 0;
            _result = new DownloadRecord(_tmpId,_tmpVideoId,_tmpCid,_tmpTitle,_tmpQuality,_tmpDownloadPath,_tmpFileName,_tmpDownloadTime,_tmpFileSize,_tmpMode,_tmpHasCover,_tmpHasDanmaku,_tmpHasSubtitle);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object isDownloaded(final String videoId, final long cid, final int quality,
      final Continuation<? super Boolean> $completion) {
    final String _sql = "SELECT EXISTS(SELECT 1 FROM download_records WHERE videoId = ? AND cid = ? AND quality = ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, videoId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, cid);
    _argIndex = 3;
    _statement.bindLong(_argIndex, quality);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Boolean>() {
      @Override
      @NonNull
      public Boolean call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Boolean _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp != 0;
          } else {
            _result = false;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRecordsByVideoId(final String videoId,
      final Continuation<? super List<DownloadRecord>> $completion) {
    final String _sql = "SELECT * FROM download_records WHERE videoId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, videoId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DownloadRecord>>() {
      @Override
      @NonNull
      public List<DownloadRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVideoId = CursorUtil.getColumnIndexOrThrow(_cursor, "videoId");
          final int _cursorIndexOfCid = CursorUtil.getColumnIndexOrThrow(_cursor, "cid");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfQuality = CursorUtil.getColumnIndexOrThrow(_cursor, "quality");
          final int _cursorIndexOfDownloadPath = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadPath");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDownloadTime = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadTime");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfHasCover = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCover");
          final int _cursorIndexOfHasDanmaku = CursorUtil.getColumnIndexOrThrow(_cursor, "hasDanmaku");
          final int _cursorIndexOfHasSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "hasSubtitle");
          final List<DownloadRecord> _result = new ArrayList<DownloadRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpVideoId;
            _tmpVideoId = _cursor.getString(_cursorIndexOfVideoId);
            final long _tmpCid;
            _tmpCid = _cursor.getLong(_cursorIndexOfCid);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpQuality;
            _tmpQuality = _cursor.getInt(_cursorIndexOfQuality);
            final String _tmpDownloadPath;
            _tmpDownloadPath = _cursor.getString(_cursorIndexOfDownloadPath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpDownloadTime;
            _tmpDownloadTime = _cursor.getLong(_cursorIndexOfDownloadTime);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpMode;
            _tmpMode = _cursor.getString(_cursorIndexOfMode);
            final boolean _tmpHasCover;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasCover);
            _tmpHasCover = _tmp != 0;
            final boolean _tmpHasDanmaku;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfHasDanmaku);
            _tmpHasDanmaku = _tmp_1 != 0;
            final boolean _tmpHasSubtitle;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfHasSubtitle);
            _tmpHasSubtitle = _tmp_2 != 0;
            _item = new DownloadRecord(_tmpId,_tmpVideoId,_tmpCid,_tmpTitle,_tmpQuality,_tmpDownloadPath,_tmpFileName,_tmpDownloadTime,_tmpFileSize,_tmpMode,_tmpHasCover,_tmpHasDanmaku,_tmpHasSubtitle);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
