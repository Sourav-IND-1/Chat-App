package com.example.chatapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class, 
        MessageEntity::class, 
        GroupEntity::class, 
        GroupMessageEntity::class
    ], 
    version = 5, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE messages ADD COLUMN mediaType TEXT")
                        database.execSQL("ALTER TABLE messages ADD COLUMN mediaFileName TEXT")
                    }
                }
                
                val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `profilePhotoUrl` TEXT, `adminId` TEXT NOT NULL, `members` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`))")
                        database.execSQL("CREATE TABLE IF NOT EXISTS `group_messages` (`messageId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isSentByMe` INTEGER NOT NULL, `mediaUrl` TEXT, `mediaType` TEXT, `isPoll` INTEGER NOT NULL, `pollId` TEXT, `pollQuestion` TEXT, `pollOptionsJson` TEXT, `userVotedOption` TEXT, PRIMARY KEY(`messageId`))")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
