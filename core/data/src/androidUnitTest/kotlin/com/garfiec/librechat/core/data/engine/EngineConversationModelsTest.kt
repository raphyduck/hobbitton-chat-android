package com.garfiec.librechat.core.data.engine

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The model this device last sent each conversation with round-trips, the latest write wins, and sign-out forgets it (26/10/2026). */
class EngineConversationModelsTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create { File(tmpFolder.root, "models.preferences_pb") }
    }

    private val store by lazy { EngineConversationModels(dataStore, Json, Dispatchers.Unconfined) }

    private val deepseek = EngineModelRef("hobbitton-chat", "deepseek-v4-pro")
    private val sonnet = EngineModelRef("hobbitton-chat", "claude-sonnet-5-5")

    @Test
    fun `a recorded model is read back, the latest write winning`() = runTest {
        assertThat(store.modelOf("ses_1")).isNull()

        store.record("ses_1", sonnet)
        store.record("ses_2", deepseek)
        store.record("ses_1", deepseek)

        assertThat(store.modelOf("ses_1")).isEqualTo(deepseek)
        assertThat(store.modelOf("ses_2")).isEqualTo(deepseek)
    }

    @Test
    fun `clearing forgets every conversation`() = runTest {
        store.record("ses_1", sonnet)

        store.clear()

        assertThat(store.modelOf("ses_1")).isNull()
    }
}
