package com.garfiec.librechat.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Where the global profile lands: under the LibreChat account when there is one, under the device
 * when there is none — the only case left on a fresh install since D-077.
 */
class GlobalProfileStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create { File(tmpFolder.root, "profile.preferences_pb") }
    }

    private val profile = GlobalProfile(instructions = "Réponds en français.", mcpServers = setOf("memoire"))

    @Test
    fun `without an account the profile is the device's, and it round-trips`() = runTest {
        val store = GlobalProfileStore(dataStore, InMemoryActiveAccountProvider(AccountState.Resolved(null)))

        store.save(profile)

        assertThat(store.current()).isEqualTo(profile)
        assertThat(store.profile.first()).isEqualTo(profile)
    }

    @Test
    fun `an account keeps its own profile, apart from the device's`() = runTest {
        val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(null))
        val store = GlobalProfileStore(dataStore, accounts)
        store.save(profile)

        accounts.set(AccountId("acct-1"))
        assertThat(store.current().instructions).isEmpty()
        store.save(GlobalProfile(instructions = "Compte de travail."))
        assertThat(store.current().instructions).isEqualTo("Compte de travail.")

        accounts.clear()
        assertThat(store.current()).isEqualTo(profile)
    }

    @Test
    fun `nothing written reads as an empty profile that is on`() = runTest {
        val store = GlobalProfileStore(dataStore, InMemoryActiveAccountProvider(AccountState.Resolved(null)))

        assertThat(store.current()).isEqualTo(GlobalProfile())
    }
}
