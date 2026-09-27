package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.client.isSameServerAuthority
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import io.ktor.util.encodeBase64

/**
 * Two credentials on the same request, and they are not interchangeable.
 *
 * `Authorization` carries the **engine's** Basic — the engine insists on its own and refuses
 * anything else. `Proxy-Authorization` carries the **portal's** bearer, which is what the reverse
 * proxy's `forward-auth` reads. Putting the bearer in `Authorization` locks the engine out; putting
 * the Basic in `Proxy-Authorization` locks the proxy out. Both gates stay distinct, and neither
 * holds the other's secret.
 *
 * One measured caveat, worth repeating because it cost an afternoon on the server side:
 * `Proxy-Authorization` is a **hop-by-hop** header (RFC 7230 §6.1). A proxy is entitled to drop it,
 * and Caddy does. The deployment re-injects it explicitly on the authentication subrequest
 * (server-side D-031); should a future edge stop doing so, every request here comes back as a
 * redirect to the portal — which is what [isPortalRedirect] catches.
 *
 * **Both headers go to one authority and nowhere else** (finding M2, 26/09/2026). Until then they
 * were put on every request of the client, and Ktor's `HttpRedirect` copies every header but
 * `Authorization` to a redirect target — so an engine, a scheduler or a proxy answering 302 towards
 * a third-party host would have handed it the portal's bearer, a token that opens the engine *and*
 * the scheduler and renews itself offline. `KtorRedirectContractTest` pins that Ktor behaviour.
 * Now the `State` phase attaches only when the request addresses the client's own authority
 * (scheme + host + port, see [isSameServerAuthority]), and the `HttpSend` interceptor — the only
 * place that sees a redirect hop — strips both headers whenever a hop leaves it and puts them back
 * when a chain comes home, the same two-way rule the gateway headers follow.
 */
class EngineAuthPlugin(
    internal val access: suspend () -> EngineAccess?,
    internal val bearer: suspend () -> String?,
    internal val renew: suspend () -> String?,
    internal val authority: (EngineAccess) -> String,
) {

    class Config {
        var access: suspend () -> EngineAccess? = { null }
        var bearer: suspend () -> String? = { null }

        /**
         * Called when the proxy says the bearer is no good. Returns the renewed bearer, or null
         * when the person has to go through the portal again.
         */
        var renew: suspend () -> String? = { null }

        /**
         * Which of the configured addresses this client speaks to — the only one its credentials
         * may reach. The engine's client keeps the default; the scheduler's client picks
         * [EngineAccess.schedulerUrl]. A blank address matches nothing, so a client whose service
         * is not configured sends no credential at all rather than the engine's to whatever host
         * the request names.
         */
        var authority: (EngineAccess) -> String = { it.baseUrl }
    }

    companion object : HttpClientPlugin<Config, EngineAuthPlugin> {
        override val key = AttributeKey<EngineAuthPlugin>("EngineAuth")
        private val RetryFlag = AttributeKey<Boolean>("EngineAuthRetried")

        override fun prepare(block: Config.() -> Unit): EngineAuthPlugin {
            val config = Config().apply(block)
            return EngineAuthPlugin(config.access, config.bearer, config.renew, config.authority)
        }

        override fun install(plugin: EngineAuthPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val engine = plugin.access() ?: return@intercept
                if (!isSameServerAuthority(context.url, plugin.authority(engine))) return@intercept
                context.applyEngineCredentials(engine, plugin.bearer())
            }

            scope.plugin(HttpSend).intercept { request ->
                val engine = plugin.access()
                if (engine == null || !isSameServerAuthority(request.url, plugin.authority(engine))) {
                    // A redirect hop off the authority — or a request that never was on it. The
                    // `State` phase ran once per call, before any hop; this is where the target
                    // of a hop is first visible.
                    request.headers.stripEngineCredentials()
                    return@intercept execute(request)
                }
                if (!request.headers.contains(HttpHeaders.Authorization)) {
                    // A chain that left the authority and came back (origin → object store →
                    // origin is an ordinary signed-URL shape). The strip above emptied the request
                    // on the way out, and a bare request to the engine reads as a login page.
                    request.applyEngineCredentials(engine, plugin.bearer())
                }
                val call = execute(request)

                val rejected = call.response.status == HttpStatusCode.Unauthorized ||
                    isPortalRedirect(call.response, engine.issuerUrl)
                if (!rejected) return@intercept call

                // Once, deliberately. A bearer refused twice means the portal session is gone, and
                // looping there turns « log in again » into a silent hammering of the server.
                if (request.attributes.getOrNull(RetryFlag) == true) return@intercept call

                val renewed = plugin.renew() ?: return@intercept call
                request.applyEngineCredentials(engine, renewed)
                request.attributes.put(RetryFlag, true)
                execute(request)
            }
        }
    }
}

/**
 * Authelia does not answer an unauthenticated request with 401. It answers **302 to the portal**,
 * because that is the right thing to do for a browser. A client that only watches for 401 sees a
 * perfectly ordinary redirect, follows it, receives the login page with status 200, and hands that
 * HTML to a JSON parser. The error it eventually reports names neither authentication nor the
 * portal.
 */
internal fun isPortalRedirect(response: HttpResponse, issuerUrl: String): Boolean {
    if (response.status != HttpStatusCode.Found && response.status != HttpStatusCode.SeeOther) {
        return false
    }
    val location = response.headers[HttpHeaders.Location] ?: return false
    val issuerHost = runCatching { Url(issuerUrl).host }.getOrNull() ?: return false
    return issuerHost.isNotEmpty() && location.contains(issuerHost, ignoreCase = true)
}

internal fun basicHeaderValue(username: String, password: String): String =
    "Basic " + "$username:$password".encodeBase64()

internal fun HttpRequestBuilder.applyEngineCredentials(access: EngineAccess, bearer: String?) {
    headers.stripEngineCredentials()
    headers.append(HttpHeaders.Authorization, basicHeaderValue(access.username, access.password))
    // Sending `Bearer null` would be worse than sending nothing: the proxy would reject a malformed
    // credential instead of treating the request as anonymous and saying so.
    if (bearer != null) {
        headers.append(HttpHeaders.ProxyAuthorization, "Bearer $bearer")
    }
}

/** Both of the engine's credentials, and only those — nothing else on the request is this plugin's. */
internal fun HeadersBuilder.stripEngineCredentials() {
    remove(HttpHeaders.Authorization)
    remove(HttpHeaders.ProxyAuthorization)
}
