package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.client.CleartextGuardPlugin
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.pluginOrNull
import org.junit.After
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier

/**
 * Which clients refuse cleartext to a public host (finding M4, 26/09/2026), asserted on the graph
 * the app actually builds — same reason as [GatewayDetectionInstallTest]: a per-client test installs
 * the plugin itself and can never notice it missing from the module.
 */
class CleartextGuardInstallTest {

    private var app: KoinApplication? = null

    @After
    fun tearDown() {
        app?.close()
    }

    private fun client(qualifier: Qualifier?): HttpClient {
        val koin = app ?: NetworkGraphTestFakes.koinApp().also { app = it }
        return NetworkGraphTestFakes.client(koin, qualifier)
    }

    @Test
    fun `the main client refuses cleartext to a public host`() {
        assertThat(client(null).pluginOrNull(CleartextGuardPlugin)).isNotNull()
    }

    @Test
    fun `the streaming client refuses cleartext to a public host`() {
        assertThat(client(KoinQualifiers.Streaming).pluginOrNull(CleartextGuardPlugin)).isNotNull()
    }

    @Test
    fun `the refresh client refuses cleartext to a public host`() {
        assertThat(client(KoinQualifiers.Refresh).pluginOrNull(CleartextGuardPlugin)).isNotNull()
    }
}
