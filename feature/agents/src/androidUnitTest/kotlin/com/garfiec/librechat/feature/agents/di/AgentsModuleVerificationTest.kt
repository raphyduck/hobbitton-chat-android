package com.garfiec.librechat.feature.agents.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import org.junit.Test
import org.koin.test.verify.verify

class AgentsModuleVerificationTest {
    @Test
    fun verifyAgentsModule() {
        agentsModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                AgentRepository::class,
                ConfigRepository::class,
                McpRepository::class,
                ServerDataStore::class,
            ),
        )
    }
}
