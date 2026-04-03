package com.librechat.android.core.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.librechat.android.core.data.db.converter.Converters
import com.librechat.android.core.data.db.dao.AgentDao
import com.librechat.android.core.data.db.dao.ConversationDao
import com.librechat.android.core.data.db.dao.ConversationTagDao
import com.librechat.android.core.data.db.dao.DraftDao
import com.librechat.android.core.data.db.dao.FileDao
import com.librechat.android.core.data.db.dao.MessageDao
import com.librechat.android.core.data.db.dao.PresetDao
import com.librechat.android.core.data.db.entity.AgentEntity
import com.librechat.android.core.data.db.entity.ConversationEntity
import com.librechat.android.core.data.db.entity.ConversationTagEntity
import com.librechat.android.core.data.db.entity.DraftEntity
import com.librechat.android.core.data.db.entity.FileEntity
import com.librechat.android.core.data.db.entity.MessageEntity
import com.librechat.android.core.data.db.entity.PresetEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        FileEntity::class,
        AgentEntity::class,
        PresetEntity::class,
        ConversationTagEntity::class,
        DraftEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
@ConstructedBy(LibreChatDatabaseConstructor::class)
abstract class LibreChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun fileDao(): FileDao
    abstract fun agentDao(): AgentDao
    abstract fun presetDao(): PresetDao
    abstract fun conversationTagDao(): ConversationTagDao
    abstract fun draftDao(): DraftDao
}

// Room KSP auto-generates the actual implementations for each platform
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LibreChatDatabaseConstructor : RoomDatabaseConstructor<LibreChatDatabase>
