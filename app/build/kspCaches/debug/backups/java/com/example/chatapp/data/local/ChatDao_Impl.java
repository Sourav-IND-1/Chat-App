package com.example.chatapp.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ChatDao_Impl implements ChatDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UserEntity> __insertionAdapterOfUserEntity;

  private final EntityInsertionAdapter<MessageEntity> __insertionAdapterOfMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkMessagesAsRead;

  private final SharedSQLiteStatement __preparedStmtOfClearMessagesWithUser;

  private final SharedSQLiteStatement __preparedStmtOfDeleteUser;

  public ChatDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUserEntity = new EntityInsertionAdapter<UserEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `users` (`userId`,`name`,`profilePhotoUrl`,`status`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserEntity entity) {
        statement.bindString(1, entity.getUserId());
        statement.bindString(2, entity.getName());
        if (entity.getProfilePhotoUrl() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getProfilePhotoUrl());
        }
        if (entity.getStatus() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getStatus());
        }
      }
    };
    this.__insertionAdapterOfMessageEntity = new EntityInsertionAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`messageId`,`senderId`,`receiverId`,`content`,`timestamp`,`isSentByMe`,`isRead`,`mediaUrl`,`mediaKey`,`mediaIv`,`mediaType`,`mediaFileName`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindString(1, entity.getMessageId());
        statement.bindString(2, entity.getSenderId());
        statement.bindString(3, entity.getReceiverId());
        statement.bindString(4, entity.getContent());
        statement.bindLong(5, entity.getTimestamp());
        final int _tmp = entity.isSentByMe() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.isRead() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        if (entity.getMediaUrl() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getMediaUrl());
        }
        if (entity.getMediaKey() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getMediaKey());
        }
        if (entity.getMediaIv() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getMediaIv());
        }
        if (entity.getMediaType() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getMediaType());
        }
        if (entity.getMediaFileName() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getMediaFileName());
        }
      }
    };
    this.__preparedStmtOfMarkMessagesAsRead = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET isRead = 1 WHERE senderId = ? AND receiverId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearMessagesWithUser = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE (senderId = ? AND receiverId = ?) OR (senderId = ? AND receiverId = ?)";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteUser = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM users WHERE userId = ?";
        return _query;
      }
    };
  }

  @Override
  public long insertUser(final UserEntity user) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfUserEntity.insertAndReturnId(user);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public long insertMessage(final MessageEntity message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfMessageEntity.insertAndReturnId(message);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void markMessagesAsRead(final String myUserId, final String theirUserId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfMarkMessagesAsRead.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, theirUserId);
    _argIndex = 2;
    _stmt.bindString(_argIndex, myUserId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfMarkMessagesAsRead.release(_stmt);
    }
  }

  @Override
  public void clearMessagesWithUser(final String myUserId, final String otherUserId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearMessagesWithUser.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, myUserId);
    _argIndex = 2;
    _stmt.bindString(_argIndex, otherUserId);
    _argIndex = 3;
    _stmt.bindString(_argIndex, otherUserId);
    _argIndex = 4;
    _stmt.bindString(_argIndex, myUserId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearMessagesWithUser.release(_stmt);
    }
  }

  @Override
  public void deleteUser(final String userId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteUser.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, userId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteUser.release(_stmt);
    }
  }

  @Override
  public Flow<List<UserEntity>> getAllContacts() {
    final String _sql = "SELECT * FROM users";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"users"}, new Callable<List<UserEntity>>() {
      @Override
      @NonNull
      public List<UserEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UserEntity> _result = new ArrayList<UserEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UserEntity _item;
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpProfilePhotoUrl;
            if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
              _tmpProfilePhotoUrl = null;
            } else {
              _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
            }
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UserEntity(_tmpUserId,_tmpName,_tmpProfilePhotoUrl,_tmpStatus);
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
  public Flow<List<UserEntity>> getContactsSortedByRecentMessage() {
    final String _sql = "\n"
            + "        SELECT u.* FROM users u\n"
            + "        LEFT JOIN messages m ON (u.userId = m.senderId OR u.userId = m.receiverId)\n"
            + "        GROUP BY u.userId\n"
            + "        ORDER BY MAX(m.timestamp) DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"users",
        "messages"}, new Callable<List<UserEntity>>() {
      @Override
      @NonNull
      public List<UserEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UserEntity> _result = new ArrayList<UserEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UserEntity _item;
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpProfilePhotoUrl;
            if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
              _tmpProfilePhotoUrl = null;
            } else {
              _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
            }
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UserEntity(_tmpUserId,_tmpName,_tmpProfilePhotoUrl,_tmpStatus);
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
  public Flow<Integer> getUnreadCount(final String myUserId, final String theirUserId) {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE senderId = ? AND receiverId = ? AND isRead = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, theirUserId);
    _argIndex = 2;
    _statement.bindString(_argIndex, myUserId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Object getAllContactsSync(final Continuation<List<UserEntity>> $completion) {
    final String _sql = "SELECT * FROM users";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<UserEntity>>() {
      @Override
      @NonNull
      public List<UserEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UserEntity> _result = new ArrayList<UserEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UserEntity _item;
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpProfilePhotoUrl;
            if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
              _tmpProfilePhotoUrl = null;
            } else {
              _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
            }
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UserEntity(_tmpUserId,_tmpName,_tmpProfilePhotoUrl,_tmpStatus);
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

  @Override
  public UserEntity getUserById(final String userId) {
    final String _sql = "SELECT * FROM users WHERE userId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final UserEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpUserId;
        _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpProfilePhotoUrl;
        if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
          _tmpProfilePhotoUrl = null;
        } else {
          _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
        }
        final String _tmpStatus;
        if (_cursor.isNull(_cursorIndexOfStatus)) {
          _tmpStatus = null;
        } else {
          _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
        }
        _result = new UserEntity(_tmpUserId,_tmpName,_tmpProfilePhotoUrl,_tmpStatus);
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
  public Flow<List<MessageEntity>> getMessagesWithUser(final String myUserId,
      final String otherUserId) {
    final String _sql = "SELECT * FROM messages WHERE (senderId = ? AND receiverId = ?) OR (senderId = ? AND receiverId = ?) ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, myUserId);
    _argIndex = 2;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 3;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 4;
    _statement.bindString(_argIndex, myUserId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfMediaKey = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaKey");
          final int _cursorIndexOfMediaIv = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaIv");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMediaFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFileName");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpMessageId;
            _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpReceiverId;
            _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsSentByMe;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
            _tmpIsSentByMe = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpMediaKey;
            if (_cursor.isNull(_cursorIndexOfMediaKey)) {
              _tmpMediaKey = null;
            } else {
              _tmpMediaKey = _cursor.getString(_cursorIndexOfMediaKey);
            }
            final String _tmpMediaIv;
            if (_cursor.isNull(_cursorIndexOfMediaIv)) {
              _tmpMediaIv = null;
            } else {
              _tmpMediaIv = _cursor.getString(_cursorIndexOfMediaIv);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMediaFileName;
            if (_cursor.isNull(_cursorIndexOfMediaFileName)) {
              _tmpMediaFileName = null;
            } else {
              _tmpMediaFileName = _cursor.getString(_cursorIndexOfMediaFileName);
            }
            _item = new MessageEntity(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpIsRead,_tmpMediaUrl,_tmpMediaKey,_tmpMediaIv,_tmpMediaType,_tmpMediaFileName);
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
  public Flow<MessageEntity> getLatestMessageForUser(final String myUserId,
      final String otherUserId) {
    final String _sql = "SELECT * FROM messages WHERE (senderId = ? AND receiverId = ?) OR (senderId = ? AND receiverId = ?) ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, myUserId);
    _argIndex = 2;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 3;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 4;
    _statement.bindString(_argIndex, myUserId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfMediaKey = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaKey");
          final int _cursorIndexOfMediaIv = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaIv");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMediaFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFileName");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpMessageId;
            _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpReceiverId;
            _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsSentByMe;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
            _tmpIsSentByMe = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpMediaKey;
            if (_cursor.isNull(_cursorIndexOfMediaKey)) {
              _tmpMediaKey = null;
            } else {
              _tmpMediaKey = _cursor.getString(_cursorIndexOfMediaKey);
            }
            final String _tmpMediaIv;
            if (_cursor.isNull(_cursorIndexOfMediaIv)) {
              _tmpMediaIv = null;
            } else {
              _tmpMediaIv = _cursor.getString(_cursorIndexOfMediaIv);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMediaFileName;
            if (_cursor.isNull(_cursorIndexOfMediaFileName)) {
              _tmpMediaFileName = null;
            } else {
              _tmpMediaFileName = _cursor.getString(_cursorIndexOfMediaFileName);
            }
            _result = new MessageEntity(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpIsRead,_tmpMediaUrl,_tmpMediaKey,_tmpMediaIv,_tmpMediaType,_tmpMediaFileName);
          } else {
            _result = null;
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
  public Flow<MessageEntity> getLastMessageFlow() {
    final String _sql = "SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfMediaKey = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaKey");
          final int _cursorIndexOfMediaIv = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaIv");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMediaFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFileName");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpMessageId;
            _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpReceiverId;
            _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsSentByMe;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
            _tmpIsSentByMe = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpMediaKey;
            if (_cursor.isNull(_cursorIndexOfMediaKey)) {
              _tmpMediaKey = null;
            } else {
              _tmpMediaKey = _cursor.getString(_cursorIndexOfMediaKey);
            }
            final String _tmpMediaIv;
            if (_cursor.isNull(_cursorIndexOfMediaIv)) {
              _tmpMediaIv = null;
            } else {
              _tmpMediaIv = _cursor.getString(_cursorIndexOfMediaIv);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMediaFileName;
            if (_cursor.isNull(_cursorIndexOfMediaFileName)) {
              _tmpMediaFileName = null;
            } else {
              _tmpMediaFileName = _cursor.getString(_cursorIndexOfMediaFileName);
            }
            _result = new MessageEntity(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpIsRead,_tmpMediaUrl,_tmpMediaKey,_tmpMediaIv,_tmpMediaType,_tmpMediaFileName);
          } else {
            _result = null;
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
  public List<MessageEntity> getMessagesByIds(final List<String> messageIds) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM messages WHERE messageId IN (");
    final int _inputSize = messageIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : messageIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
      final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
      final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
      final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
      final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
      final int _cursorIndexOfMediaKey = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaKey");
      final int _cursorIndexOfMediaIv = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaIv");
      final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
      final int _cursorIndexOfMediaFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFileName");
      final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MessageEntity _item_1;
        final String _tmpMessageId;
        _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
        final String _tmpSenderId;
        _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
        final String _tmpReceiverId;
        _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
        final String _tmpContent;
        _tmpContent = _cursor.getString(_cursorIndexOfContent);
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        final boolean _tmpIsSentByMe;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
        _tmpIsSentByMe = _tmp != 0;
        final boolean _tmpIsRead;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
        _tmpIsRead = _tmp_1 != 0;
        final String _tmpMediaUrl;
        if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
          _tmpMediaUrl = null;
        } else {
          _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
        }
        final String _tmpMediaKey;
        if (_cursor.isNull(_cursorIndexOfMediaKey)) {
          _tmpMediaKey = null;
        } else {
          _tmpMediaKey = _cursor.getString(_cursorIndexOfMediaKey);
        }
        final String _tmpMediaIv;
        if (_cursor.isNull(_cursorIndexOfMediaIv)) {
          _tmpMediaIv = null;
        } else {
          _tmpMediaIv = _cursor.getString(_cursorIndexOfMediaIv);
        }
        final String _tmpMediaType;
        if (_cursor.isNull(_cursorIndexOfMediaType)) {
          _tmpMediaType = null;
        } else {
          _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
        }
        final String _tmpMediaFileName;
        if (_cursor.isNull(_cursorIndexOfMediaFileName)) {
          _tmpMediaFileName = null;
        } else {
          _tmpMediaFileName = _cursor.getString(_cursorIndexOfMediaFileName);
        }
        _item_1 = new MessageEntity(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpIsRead,_tmpMediaUrl,_tmpMediaKey,_tmpMediaIv,_tmpMediaType,_tmpMediaFileName);
        _result.add(_item_1);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public void deleteMessages(final List<String> messageIds) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("DELETE FROM messages WHERE messageId IN (");
    final int _inputSize = messageIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    for (String _item : messageIds) {
      _stmt.bindString(_argIndex, _item);
      _argIndex++;
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
