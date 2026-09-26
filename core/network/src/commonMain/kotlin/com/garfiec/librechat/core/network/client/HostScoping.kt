package com.garfiec.librechat.core.network.client

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * True when [requestUrl] addresses exactly the server [baseUrl] identifies — **scheme, host and
 * effective port all matching**. The one gate for every credential the app attaches: the session
 * bearer ([AuthInterceptorPlugin]), the user-configured gateway headers ([ServerHeadersPlugin]),
 * and the engine's Basic and portal bearer (`EngineAuthPlugin`).
 *
 * Until 26/09/2026 the bearer had its own, looser rule — host only, and open when the base URL was
 * unknown (finding M3). That let a server-supplied absolute URL on the same host but over `http://`
 * or another port collect the session bearer: in cleartext in the first case, at another service in
 * the second. `FilesApi.downloadFromUrl` fetches exactly such URLs. Two properties of this rule,
 * both deliberate:
 *
 * - **Scheme and port count.** A same-host `http://` downgrade would otherwise pass and put a
 *   secret on the wire in cleartext; a same-host other-port URL would hand it to whatever else
 *   listens on that machine.
 * - **Fail closed.** An unknown or unparseable base URL yields false, so a request whose server
 *   can't be established carries no credential. The bearer's old cold-start case — a request built
 *   before the base URL resolved — is covered upstream: [SwitchBarrierPlugin] awaits the URL before
 *   the request is built, and the refresh client carries no bearer at all.
 *
 * [baseUrl] is the request's snapshotted server URL when the [SwitchBarrierPlugin] is installed, so
 * an account switch mid-request scopes the credential to the account the request was snapshotted
 * for (never the live one), keeping A's bearer off B's host.
 */
internal fun isSameServerAuthority(requestUrl: URLBuilder, baseUrl: String?): Boolean {
    if (baseUrl.isNullOrEmpty()) return false
    val base = runCatching { Url(baseUrl) }.getOrNull() ?: return false
    if (base.host.isEmpty() || requestUrl.host.isEmpty()) return false
    return requestUrl.host.equals(base.host, ignoreCase = true) &&
        requestUrl.protocol.name.equals(base.protocol.name, ignoreCase = true) &&
        requestUrl.effectivePort() == base.port
}

/**
 * The port a [URLBuilder] will actually dial. `URLBuilder.port` reports [DEFAULT_PORT] (a sentinel,
 * not a real port) when no port was written explicitly, whereas `Url.port` already resolves to the
 * protocol default — comparing the two raw would make `https://host` and `https://host:443` differ.
 */
private fun URLBuilder.effectivePort(): Int =
    if (port == DEFAULT_PORT) protocol.defaultPort else port
