package com.garfiec.librechat.core.data.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import com.garfiec.librechat.core.data.pricing.ModelPriceSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.junit.Test
import org.koin.test.verify.verify

class DataModuleVerificationTest {
    @Test
    fun verifyDataModule() {
        dataModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                DataStore::class,
                Json::class,
                CoroutineDispatcher::class,
                CoroutineScope::class,
                // Optional, and resolved with `getOrNull`: the price table's source is bound by
                // `engineModule`. Koin's verifier reads the constructor's declared types and cannot
                // see that the lookup tolerates absence, so it is named here.
                ModelPriceSource::class,
            ),
        )
    }
}
