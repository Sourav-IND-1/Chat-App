package com.example.chatapp.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class GroupDao_Impl implements GroupDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<GroupEntity> __insertionAdapterOfGroupEntity;

  private final Converters __converters = new Converters();

  private final EntityInsertionAdapter<GroupMessageEntity> __insertionAdapterOfGroupMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteGroup;

  private final SharedSQLiteStatement __preparedStmtOfClearMessagesForGroup;

  public GroupDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGroupEntity = new EntityInsertionAdapter<GroupEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `groups` (`groupId`,`name`,`description`,`profilePhotoUrl`,`adminId`,`members`,`createdAt`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GroupEntity entity) {
        statement.bindString(1, entity.getGroupId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getDescription());
        if (entity.getProfilePhotoUrl() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getProfilePhotoUrl());
        }
        statement.bindString(5, entity.getAdminId());
        final String _tmp = __converters.fromStringList(entity.getMembers());
        if (_tmp == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, _tmp);
        }
        statement.bindLong(7, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfGroupMessageEntity = new EntityInsertionAdapter<GroupMessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `group_messages` (`messageId`,`groupId`,`senderId`,`senderName`,`content`,`timestamp`,`isSentByMe`,`mediaUrl`,`mediaType`,`isPoll`,`pollId`,`pollQuestion`,`pollOptionsJson`,`userVotedOption`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GroupMessageEntity entity) {
        statement.bindString(1, entity.getMessageId());
        statement.bindString(2, entity.getGroupId());
        statement.bindString(3, entity.getSenderId());
        statement.bindString(4, entity.getSenderName());
        statement.bindString(5, entity.getContent());
        statement.bindLong(6, entity.getTimestamp());
        final int _tmp = entity.isSentByMe() ? 1 : 0;
        statement.bindLong(7, _tmp);
        if (entity.getMediaUrl() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getMediaUrl());
        }
        if (entity.getMediaType() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getMediaType());
        }
        final int _tmp_1 = entity.isPoll() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        if (entity.getPollId() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getPollId());
        }
        if (entity.getPollQuestion() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getPollQuestion());
        }
        if (entity.getPollOptionsJson() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getPollOptionsJson());
        }
        if (entity.getUserVotedOption() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getUserVotedOption());
        }
      }
    };
    this.__preparedStmtOfDeleteGroup = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM groups WHERE groupId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearMessagesForGroup = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM group_messages WHERE groupId = ?";
        return _query;
      }
    };
  }

  @Override
  public long insertGroup(final GroupEntity group) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfGroupEntity.insertAndReturnId(group);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertGroups(final List<GroupEntity> groups) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfGroupEntity.insert(groups);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public long insertGroupMessage(final GroupMessageEntity message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfGroupMessageEntity.insertAndReturnId(message);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteGroup(final String groupId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteGroup.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, groupId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteGroup.release(_stmt);
    }
  }

  @Override
  public void clearMessagesForGroup(final String groupId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearMessagesForGroup.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, groupId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearMessagesForGroup.release(_stmt);
    }
  }

  @Override
  public Flow<List<GroupEntity>> getAllGroups() {
    final String _sql = "SELECT * FROM groups ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"groups"}, new Callable<List<GroupEntity>>() {
      @Override
      @NonNull
      public List<GroupEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
          final int _cursorIndexOfAdminId = CursorUtil.getColumnIndexOrThrow(_cursor, "adminId");
          final int _cursorIndexOfMembers = CursorUtil.getColumnIndexOrThrow(_cursor, "members");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<GroupEntity> _result = new ArrayList<GroupEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GroupEntity _item;
            final String _tmpGroupId;
            _tmpGroupId = _cursor.getString(_cursorIndexOfGroupId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpProfilePhotoUrl;
            if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
              _tmpProfilePhotoUrl = null;
            } else {
              _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
            }
            final String _tmpAdminId;
            _tmpAdminId = _cursor.getString(_cursorIndexOfAdminId);
            final List<String> _tmpMembers;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfMembers)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfMembers);
            }
            final List<String> _tmp_1 = __converters.toStringList(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.util.List<java.lang.String>', but it was NULL.");
            } else {
              _tmpMembers = _tmp_1;
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new GroupEntity(_tmpGroupId,_tmpName,_tmpDescription,_tmpProfilePhotoUrl,_tmpAdminId,_tmpMembers,_tmpCreatedAt);
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
  public GroupEntity getGroupById(final String groupId) {
    final String _sql = "SELECT * FROM groups WHERE groupId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, groupId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfProfilePhotoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "profilePhotoUrl");
      final int _cursorIndexOfAdminId = CursorUtil.getColumnIndexOrThrow(_cursor, "adminId");
      final int _cursorIndexOfMembers = CursorUtil.getColumnIndexOrThrow(_cursor, "members");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final GroupEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpGroupId;
        _tmpGroupId = _cursor.getString(_cursorIndexOfGroupId);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpDescription;
        _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        final String _tmpProfilePhotoUrl;
        if (_cursor.isNull(_cursorIndexOfProfilePhotoUrl)) {
          _tmpProfilePhotoUrl = null;
        } else {
          _tmpProfilePhotoUrl = _cursor.getString(_cursorIndexOfProfilePhotoUrl);
        }
        final String _tmpAdminId;
        _tmpAdminId = _cursor.getString(_cursorIndexOfAdminId);
        final List<String> _tmpMembers;
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfMembers)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfMembers);
        }
        final List<String> _tmp_1 = __converters.toStringList(_tmp);
        if (_tmp_1 == null) {
          throw new IllegalStateException("Expected NON-NULL 'java.util.List<java.lang.String>', but it was NULL.");
        } else {
          _tmpMembers = _tmp_1;
        }
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _result = new GroupEntity(_tmpGroupId,_tmpName,_tmpDescription,_tmpProfilePhotoUrl,_tmpAdminId,_tmpMembers,_tmpCreatedAt);
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
  public Flow<List<GroupMessageEntity>> getMessagesForGroup(final String groupId) {
    final String _sql = "SELECT * FROM group_messages WHERE groupId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, groupId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"group_messages"}, new Callable<List<GroupMessageEntity>>() {
      @Override
      @NonNull
      public List<GroupMessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfIsPoll = CursorUtil.getColumnIndexOrThrow(_cursor, "isPoll");
          final int _cursorIndexOfPollId = CursorUtil.getColumnIndexOrThrow(_cursor, "pollId");
          final int _cursorIndexOfPollQuestion = CursorUtil.getColumnIndexOrThrow(_cursor, "pollQuestion");
          final int _cursorIndexOfPollOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "pollOptionsJson");
          final int _cursorIndexOfUserVotedOption = CursorUtil.getColumnIndexOrThrow(_cursor, "userVotedOption");
          final List<GroupMessageEntity> _result = new ArrayList<GroupMessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GroupMessageEntity _item;
            final String _tmpMessageId;
            _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
            final String _tmpGroupId;
            _tmpGroupId = _cursor.getString(_cursorIndexOfGroupId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsSentByMe;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
            _tmpIsSentByMe = _tmp != 0;
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final boolean _tmpIsPoll;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsPoll);
            _tmpIsPoll = _tmp_1 != 0;
            final String _tmpPollId;
            if (_cursor.isNull(_cursorIndexOfPollId)) {
              _tmpPollId = null;
            } else {
              _tmpPollId = _cursor.getString(_cursorIndexOfPollId);
            }
            final String _tmpPollQuestion;
            if (_cursor.isNull(_cursorIndexOfPollQuestion)) {
              _tmpPollQuestion = null;
            } else {
              _tmpPollQuestion = _cursor.getString(_cursorIndexOfPollQuestion);
            }
            final String _tmpPollOptionsJson;
            if (_cursor.isNull(_cursorIndexOfPollOptionsJson)) {
              _tmpPollOptionsJson = null;
            } else {
              _tmpPollOptionsJson = _cursor.getString(_cursorIndexOfPollOptionsJson);
            }
            final String _tmpUserVotedOption;
            if (_cursor.isNull(_cursorIndexOfUserVotedOption)) {
              _tmpUserVotedOption = null;
            } else {
              _tmpUserVotedOption = _cursor.getString(_cursorIndexOfUserVotedOption);
            }
            _item = new GroupMessageEntity(_tmpMessageId,_tmpGroupId,_tmpSenderId,_tmpSenderName,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpMediaUrl,_tmpMediaType,_tmpIsPoll,_tmpPollId,_tmpPollQuestion,_tmpPollOptionsJson,_tmpUserVotedOption);
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
  public Flow<GroupMessageEntity> getLatestMessageForGroup(final String groupId) {
    final String _sql = "SELECT * FROM group_messages WHERE groupId = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, groupId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"group_messages"}, new Callable<GroupMessageEntity>() {
      @Override
      @Nullable
      public GroupMessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfGroupId = CursorUtil.getColumnIndexOrThrow(_cursor, "groupId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsSentByMe = CursorUtil.getColumnIndexOrThrow(_cursor, "isSentByMe");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfIsPoll = CursorUtil.getColumnIndexOrThrow(_cursor, "isPoll");
          final int _cursorIndexOfPollId = CursorUtil.getColumnIndexOrThrow(_cursor, "pollId");
          final int _cursorIndexOfPollQuestion = CursorUtil.getColumnIndexOrThrow(_cursor, "pollQuestion");
          final int _cursorIndexOfPollOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "pollOptionsJson");
          final int _cursorIndexOfUserVotedOption = CursorUtil.getColumnIndexOrThrow(_cursor, "userVotedOption");
          final GroupMessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpMessageId;
            _tmpMessageId = _cursor.getString(_cursorIndexOfMessageId);
            final String _tmpGroupId;
            _tmpGroupId = _cursor.getString(_cursorIndexOfGroupId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsSentByMe;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSentByMe);
            _tmpIsSentByMe = _tmp != 0;
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final boolean _tmpIsPoll;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsPoll);
            _tmpIsPoll = _tmp_1 != 0;
            final String _tmpPollId;
            if (_cursor.isNull(_cursorIndexOfPollId)) {
              _tmpPollId = null;
            } else {
              _tmpPollId = _cursor.getString(_cursorIndexOfPollId);
            }
            final String _tmpPollQuestion;
            if (_cursor.isNull(_cursorIndexOfPollQuestion)) {
              _tmpPollQuestion = null;
            } else {
              _tmpPollQuestion = _cursor.getString(_cursorIndexOfPollQuestion);
            }
            final String _tmpPollOptionsJson;
            if (_cursor.isNull(_cursorIndexOfPollOptionsJson)) {
              _tmpPollOptionsJson = null;
            } else {
              _tmpPollOptionsJson = _cursor.getString(_cursorIndexOfPollOptionsJson);
            }
            final String _tmpUserVotedOption;
            if (_cursor.isNull(_cursorIndexOfUserVotedOption)) {
              _tmpUserVotedOption = null;
            } else {
              _tmpUserVotedOption = _cursor.getString(_cursorIndexOfUserVotedOption);
            }
            _result = new GroupMessageEntity(_tmpMessageId,_tmpGroupId,_tmpSenderId,_tmpSenderName,_tmpContent,_tmpTimestamp,_tmpIsSentByMe,_tmpMediaUrl,_tmpMediaType,_tmpIsPoll,_tmpPollId,_tmpPollQuestion,_tmpPollOptionsJson,_tmpUserVotedOption);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
