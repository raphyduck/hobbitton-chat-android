package com.garfiec.librechat.feature.agents.di

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
                com.garfiec.librechat.core.data.repository.AgentRepository::class,
                com.garfiec.librechat.core.data.repository.ConfigRepository::class,
                com.garfiec.librechat.core.data.repository.McpRepository::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
            ),
        )
    }
}
