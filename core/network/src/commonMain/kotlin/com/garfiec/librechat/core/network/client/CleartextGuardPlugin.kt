package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.network.CleartextPolicy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.util.AttributeKey

/**
 * Raised instead of sending a request in the clear to a host outside the private ranges. Carries the
 * host only — never the path or the query, which is where a presigned URL keeps its signature.
 */
class CleartextRefusedException(val host: String) :
    Exception("Refusing to send http:// in the clear to $host — only a local or private-network host may use http://")

/**
 * Refuses an `http://` send to any host that is not private (finding M4, 26/09/2026).
 *
 * Android's `network_security_config` permits cleartext globally because the one legitimate case —
 * a self-hosted server on the user's LAN — cannot be written as a domain rule, and the resource
 * cannot express an address range. That left every absolute `http://` URL reachable: not only the
 * server address the user typed (which the entry screens now refuse, see [CleartextPolicy]) but any
 * `http://` URL the server hands back — a download URL, an avatar — and any redirect towards one.
 * This plugin is the enforcement point those cases share.
 *
 * An `HttpSend` interceptor rather than a request-pipeline phase, for the reason
 * [ServerHeadersPlugin] gives: Ktor's `HttpRedirect` re-executes below the request pipeline, so
 * only `HttpSend` sees the redirect target. Ktor refuses an `https → http` downgrade on redirect by
 * itself; this covers the `http → http` hop from a private host to a public one, and the direct
 * absolute URL that no redirect guard ever sees.
 *
 * Installed on **every** client — main, streaming, refresh, engine, scheduler and portal — for the
 * same reason as [GatewayDetectionPlugin]: the absence is silent. `CleartextGuardInstallTest` and
 * `EnginePortalClientTest` pin the set.
 */
class CleartextGuardPlugin private constructor() {

    class Config

    companion object : HttpClientPlugin<Config, CleartextGuardPlugin> {
        override val key = AttributeKey<CleartextGuardPlugin>("CleartextGuard")

        override fun prepare(block: Config.() -> Unit): CleartextGuardPlugin = CleartextGuardPlugin()

        override fun install(plugin: CleartextGuardPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val scheme = request.url.protocol.name
                val cleartext = scheme.equals("http", ignoreCase = true) || scheme.equals("ws", ignoreCase = true)
                if (cleartext && !CleartextPolicy.isPrivateHost(request.url.host)) {
                    throw CleartextRefusedException(request.url.host)
                }
                execute(request)
            }
        }
    }
}
