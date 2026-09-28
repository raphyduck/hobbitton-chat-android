package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.SessionManager
import com.garfiec.librechat.core.data.datastore.AccountRegistry
import com.garfiec.librechat.core.data.util.SessionTaskRunner
import com.garfiec.librechat.core.network.api.AuthApi
import com.garfiec.librechat.core.network.api.UserApi
import com.garfiec.librechat.core.network.client.RequestIdentity
import com.garfiec.librechat.core.network.client.SwitchGate
import com.garfiec.librechat.core.network.client.TokenManager
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The hobbitton sign-out hooks (D-076) on the real `logout()`: they run inside its teardown, with
 * the server URL captured before it, whatever happened to the revocation request.
 */
class AuthRepositoryImplSignOutTest {

    private val accountRegistry = mockk<AccountRegistry>(relaxed = true)
    private val activeAccountProvider = mockk<ActiveAccountProvider>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)
    private val switchGate = mockk<SwitchGate>(relaxed = true)

    @Before
    fun setUp() {
        every { activeAccountProvider.state } returns MutableStateFlow(AccountState.Warming)
        every { accountSwitcher.pendingAdd } returns null
        coEvery { switchGate.captureSnapshot(any()) } returns RequestIdentity(
            baseUrl = "https://chat.example.com",
            accountId = "acct-1",
            bearer = null,
        )
    }

    private fun repo(hooks: List<SignOutHook>, offline: Boolean = false): AuthRepositoryImpl {
        val engine = MockEngine {
            if (offline) throw IOException("network is unreachable")
            respond("{}", HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            defaultRequest { url("https://chat.example.com") }
        }
        return AuthRepositoryImpl(
            authApi = AuthApi(client),
            userApi = mockk<UserApi>(relaxed = true),
            tokenManager = mockk<TokenManager>(relaxed = true),
            sessionCacheCleaner = mockk<SessionCacheCleaner>(relaxed = true),
            sessionTaskRunner = mockk<SessionTaskRunner>(relaxed = true),
            accountSessionEstablisher = mockk<AccountSessionEstablisher>(relaxed = true),
            accountRegistry = accountRegistry,
            activeAccountProvider = activeAccountProvider,
            sessionManager = mockk<SessionManager>(relaxed = true),
            accountSwitcher = accountSwitcher,
            switchGate = switchGate,
            signOutHooks = hooks,
        )
    }

    @Test
    fun `logout runs the hooks with the server it signed out of`() = runTest {
        val seen = mutableListOf<String?>()

        repo(listOf(SignOutHook { seen += it })).logout()

        assertThat(seen).containsExactly("https://chat.example.com")
        coVerify { accountSwitcher.remove("acct-1") }
    }

    @Test
    fun `the hooks run even when the revocation request fails`() = runTest {
        // Revocation is best effort; the local teardown is what must not be skipped — and the
        // portal's tokens are part of it now.
        val seen = mutableListOf<String?>()

        repo(listOf(SignOutHook { seen += it }), offline = true).logout()

        assertThat(seen).containsExactly("https://chat.example.com")
    }

    @Test
    fun `a failing hook does not keep the next one from running`() = runTest {
        val seen = mutableListOf<String>()
        val hooks = listOf(
            SignOutHook { error("cookie jar unavailable") },
            SignOutHook { seen += "second" },
        )

        repo(hooks).logout()

        assertThat(seen).containsExactly("second")
    }

    @Test
    fun `without hooks, logout is the upstream one`() = runTest {
        repo(emptyList()).logout()

        coVerify { accountSwitcher.remove("acct-1") }
    }
}
