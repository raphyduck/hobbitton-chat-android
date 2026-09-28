package com.garfiec.librechat

import android.app.Application
import android.content.Context
import com.garfiec.librechat.core.data.datastore.GlobalProfileEditor
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.data.pricing.ModelPriceSource
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.shared.di.sharedKoinModules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.reflect.KClass

class KoinGraphVerificationTest {

    /**
     * Integration-level Koin graph verification over the shared module list
     * ([sharedKoinModules]).
     *
     * Koin's verify() operates per-module and cannot resolve definitions
     * from other modules. This test whitelists all cross-module and
     * framework types so every module is verified in a single test class.
     * If a type is renamed or removed, this test will catch it.
     *
     * Scope: this JVM test resolves the shared list against the Android actuals
     * (`networkModule.includes(networkPlatformModule)` binds the OkHttp engine).
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyFullKoinGraph() {
        val extraTypes = mutableListOf<KClass<*>>(
            // Android framework
            Context::class,
            Application::class,
            // core:common provides
            CoroutineDispatcher::class,
            CoroutineScope::class,
            // core:data provides; verify() resolves one module at a time
            ThemeDataStore::class,
            MissionReadingPositions::class,
            SessionKindStore::class,
            GlobalProfileEditor::class,
            // Bound by `engineModule` / `tasksModule`, which the application starts next to this
            // list, and resolved with `getOrNull` by the shell, the sign-in and the price cache. The
            // verifier reads the declared types and cannot see that.
            ModelPriceSource::class,
            PortalTasksSignIn::class,
            EngineSettingsStore::class,
            EngineTokenStore::class,
            PortalSignOut::class,
            EngineMissionRepository::class,
        )

        // Types whose libraries aren't on the app test classpath (transitive
        // implementation deps). Resolve via reflection at runtime.
        val reflectionTypes = listOf(
            "kotlinx.serialization.json.Json",
            "androidx.datastore.core.DataStore",
        )
        reflectionTypes.forEach { className ->
            extraTypes.add(Class.forName(className).kotlin)
        }

        sharedKoinModules.forEach { module ->
            module.verify(extraTypes = extraTypes)
        }
    }
}
