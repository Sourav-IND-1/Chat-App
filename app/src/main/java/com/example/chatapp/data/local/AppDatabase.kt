package com.example.chatapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(entities = [UserEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    .addCallback(PrepopulateCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // Use raw SQL since INSTANCE is not yet available during onCreate
            // Insert user1
            db.execSQL(
                "INSERT INTO users (userId, name, profilePhotoUrl, status) VALUES ('user1', 'User 1', NULL, 'Hey there! I am using ChatApp')"
            )

            val now = System.currentTimeMillis()
            val msgId1 = UUID.randomUUID().toString()
            val msgId2 = UUID.randomUUID().toString()

            // Message 1: user1 sends "hi" to admin (received by admin)
            db.execSQL(
                "INSERT INTO messages (messageId, senderId, receiverId, content, timestamp, isSentByMe) VALUES ('$msgId1', 'user1', 'admin', 'hi', ${now - 60000}, 0)"
            )

            // Message 2: admin replies "how are you" (sent by admin)
            db.execSQL(
                "INSERT INTO messages (messageId, senderId, receiverId, content, timestamp, isSentByMe) VALUES ('$msgId2', 'admin', 'user1', 'how are you', $now, 1)"
            )
        }
    }
}
