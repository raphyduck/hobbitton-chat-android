package com.garfiec.librechat.feature.conversations.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.extensions.dayBoundaryReferences
import com.garfiec.librechat.feature.conversations.drawer.DrawerViewModel
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ConversationImporter
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    single {
        ConversationExporter(
            conversationRepository = get(),
            messageRepository = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    single {
        ConversationImporter(
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }

    // Clock input for date grouping: emits at each local midnight so an open list re-buckets
    // instead of showing yesterday's sections. Registered (rather than defaulted) so
    // ConversationListViewModel can stay on `viewModelOf` and keep its `verify()` coverage.
    single<Flow<RelativeTimeReference>> { dayBoundaryReferences() }

    viewModelOf(::ConversationListViewModel)
    viewModelOf(::ArchivedConversationsViewModel)
    viewModelOf(::ProjectsViewModel)
    // Drawer-data half of the nav shell's NavHostViewModel. Spelled out rather than `viewModelOf`
    // for one argument: the mission source is bound by the Android-only engine graph (D-034), and
    // `getOrNull` is what lets iOS build a drawer of chats instead of failing to build the graph.
    @Suppress("DeprecatedKoinApi")
    viewModel {
        DrawerViewModel(
            conversationRepository = get(),
            roleRepository = get(),
            tagRepository = get(),
            projectRepository = get(),
            configRepository = get(),
            shareRepository = get(),
            conversationExporter = get(),
            activeAccountProvider = get(),
            recentMissionsSource = getOrNull(),
        )
    }
    // projectId arrives from the navigation layer via parametersOf.
    @Suppress("DeprecatedKoinApi")
    viewModel { params ->
        ProjectChatsViewModel(
            projectId = params.get(),
            conversationRepository = get(),
            tagRepository = get(),
            shareRepository = get(),
            conversationExporter = get(),
            roleRepository = get(),
            configRepository = get(),
        )
    }
}
