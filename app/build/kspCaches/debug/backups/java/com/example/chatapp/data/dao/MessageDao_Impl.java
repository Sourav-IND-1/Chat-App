package com.example.chatapp.data.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.chatapp.data.model.Message;
import java.lang.Class;
import java.lang.Exception;
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
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Message> __insertionAdapterOfMessage;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessage = new EntityInsertionAdapter<Message>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`messageId`,`senderId`,`receiverId`,`content`,`timestamp`,`messageType`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Message entity) {
        statement.bindLong(1, entity.getMessageId());
        statement.bindString(2, entity.getSenderId());
        statement.bindString(3, entity.getReceiverId());
        statement.bindString(4, entity.getContent());
        statement.bindLong(5, entity.getTimestamp());
        statement.bindString(6, entity.getMessageType());
      }
    };
  }

  @Override
  public void insertMessage(final Message message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMessage.insert(message);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public Flow<List<Message>> getMessagesBetweenUsers(final String myUserId,
      final String otherUserId) {
    final String _sql = "\n"
            + "        SELECT * FROM messages \n"
            + "        WHERE (senderId = ? AND receiverId = ?) \n"
            + "        OR (senderId = ? AND receiverId = ?)\n"
            + "        ORDER BY timestamp ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, myUserId);
    _argIndex = 2;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 3;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 4;
    _statement.bindString(_argIndex, myUserId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<Message>>() {
      @Override
      @NonNull
      public List<Message> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfMessageType = CursorUtil.getColumnIndexOrThrow(_cursor, "messageType");
          final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Message _item;
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpReceiverId;
            _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpMessageType;
            _tmpMessageType = _cursor.getString(_cursorIndexOfMessageType);
            _item = new Message(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpMessageType);
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
  public Flow<Message> getLatestMessageWithUser(final String myUserId, final String otherUserId) {
    final String _sql = "\n"
            + "        SELECT * FROM messages \n"
            + "        WHERE (senderId = ? AND receiverId = ?) \n"
            + "        OR (senderId = ? AND receiverId = ?)\n"
            + "        ORDER BY timestamp DESC LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, myUserId);
    _argIndex = 2;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 3;
    _statement.bindString(_argIndex, otherUserId);
    _argIndex = 4;
    _statement.bindString(_argIndex, myUserId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Message>() {
      @Override
      @Nullable
      public Message call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfReceiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfMessageType = CursorUtil.getColumnIndexOrThrow(_cursor, "messageType");
          final Message _result;
          if (_cursor.moveToFirst()) {
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpReceiverId;
            _tmpReceiverId = _cursor.getString(_cursorIndexOfReceiverId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpMessageType;
            _tmpMessageType = _cursor.getString(_cursorIndexOfMessageType);
            _result = new Message(_tmpMessageId,_tmpSenderId,_tmpReceiverId,_tmpContent,_tmpTimestamp,_tmpMessageType);
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
