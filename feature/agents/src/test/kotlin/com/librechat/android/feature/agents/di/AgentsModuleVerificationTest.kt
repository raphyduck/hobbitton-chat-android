package com.librechat.android.feature.agents.di

import org.junit.Test
import org.koin.test.verify.verify

class AgentsModuleVerificationTest {
    @Test
    fun verifyAgentsModule() {
        agentsModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.librechat.android.core.data.repository.AgentRepository::class,
                com.librechat.android.core.data.repository.ConfigRepository::class,
                com.librechat.android.core.data.repository.McpRepository::class,
                com.librechat.android.core.data.datastore.ServerDataStore::class,
            ),
        )
    }
}
