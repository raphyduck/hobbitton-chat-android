package com.librechat.android.feature.conversations.di

import com.librechat.android.feature.conversations.export.ConversationExporter
import com.librechat.android.feature.conversations.export.ConversationImporter
import com.librechat.android.feature.conversations.viewmodel.ArchivedConversationsViewModel
import com.librechat.android.feature.conversations.viewmodel.ConversationListViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    singleOf(::ConversationExporter)
    singleOf(::ConversationImporter)

    viewModelOf(::ConversationListViewModel)
    viewModelOf(::ArchivedConversationsViewModel)
}
