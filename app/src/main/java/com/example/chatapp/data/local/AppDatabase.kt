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
        GroupMessageEntity::class,
        GroupJoinRequestEntity::class
    ], 
    version = 10, 
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

                val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN readByCount INTEGER NOT NULL DEFAULT 0")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN isReadByMe INTEGER NOT NULL DEFAULT 0")
                    }
                }

                val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `groups` ADD COLUMN groupMasterKey TEXT")
                        database.execSQL("CREATE TABLE IF NOT EXISTS `group_join_requests` (`requestId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `requesterId` TEXT NOT NULL, `requesterName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`requestId`))")
                    }
                }

                val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `groups` ADD COLUMN hasExited INTEGER NOT NULL DEFAULT 0")
                    }
                }

                val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        // Direct messages table migration
                        database.execSQL("ALTER TABLE messages ADD COLUMN isGroupInvite INTEGER NOT NULL DEFAULT 0")
                        database.execSQL("ALTER TABLE messages ADD COLUMN inviteGroupId TEXT")
                        database.execSQL("ALTER TABLE messages ADD COLUMN inviteGroupName TEXT")
                        database.execSQL("ALTER TABLE messages ADD COLUMN inviteStatus TEXT NOT NULL DEFAULT 'UNSENT'")

                        // Group messages table migration
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN isGroupInvite INTEGER NOT NULL DEFAULT 0")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN inviteGroupId TEXT")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN inviteGroupName TEXT")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN inviteStatus TEXT NOT NULL DEFAULT 'UNSENT'")
                    }
                }

                val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN mediaKey TEXT")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN mediaIv TEXT")
                        database.execSQL("ALTER TABLE group_messages ADD COLUMN mediaFileName TEXT")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
