package com.garfiec.librechat.feature.auth.di

import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import org.junit.Test
import org.koin.test.verify.verify

class AuthModuleVerificationTest {
    @Test
    fun verifyAuthModule() {
        authModule.verify(
            extraTypes = listOf(
                // The portal sign-in (D-076, D-077): the round trip and the addresses are bound by
                // engineModule, resolved with getOrNull. The verifier reads the declared type and
                // cannot see that.
                PortalTasksSignIn::class,
                EngineSettingsStore::class,
            ),
        )
    }
}
