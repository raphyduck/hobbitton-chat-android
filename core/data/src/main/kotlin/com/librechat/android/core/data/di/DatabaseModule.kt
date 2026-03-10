package com.librechat.android.core.data.di

import android.content.Context
import androidx.room.Room
import com.librechat.android.core.data.db.LibreChatDatabase
import com.librechat.android.core.data.db.dao.AgentDao
import com.librechat.android.core.data.db.dao.ConversationDao
import com.librechat.android.core.data.db.dao.ConversationTagDao
import com.librechat.android.core.data.db.dao.DraftDao
import com.librechat.android.core.data.db.dao.FileDao
import com.librechat.android.core.data.db.dao.MessageDao
import com.librechat.android.core.data.db.dao.PresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LibreChatDatabase =
        Room.databaseBuilder(
            context,
            LibreChatDatabase::class.java,
            "librechat.db",
        ).build()

    @Provides fun provideConversationDao(db: LibreChatDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideMessageDao(db: LibreChatDatabase): MessageDao = db.messageDao()
    @Provides fun provideFileDao(db: LibreChatDatabase): FileDao = db.fileDao()
    @Provides fun provideAgentDao(db: LibreChatDatabase): AgentDao = db.agentDao()
    @Provides fun providePresetDao(db: LibreChatDatabase): PresetDao = db.presetDao()
    @Provides fun provideConversationTagDao(db: LibreChatDatabase): ConversationTagDao = db.conversationTagDao()
    @Provides fun provideDraftDao(db: LibreChatDatabase): DraftDao = db.draftDao()
}
