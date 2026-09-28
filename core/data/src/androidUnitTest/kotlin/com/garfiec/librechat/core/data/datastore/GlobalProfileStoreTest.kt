package com.garfiec.librechat.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The global profile is the device's, and it round-trips. */
class GlobalProfileStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create { File(tmpFolder.root, "profile.preferences_pb") }
    }

    private val profile = GlobalProfile(instructions = "Réponds en français.", mcpServers = setOf("memoire"))

    @Test
    fun `the profile round-trips`() = runTest {
        val store = GlobalProfileStore(dataStore)

        store.save(profile)

        assertThat(store.current()).isEqualTo(profile)
    }

    @Test
    fun `nothing written reads as an empty profile that is on`() = runTest {
        val store = GlobalProfileStore(dataStore)

        assertThat(store.current()).isEqualTo(GlobalProfile())
    }
}
