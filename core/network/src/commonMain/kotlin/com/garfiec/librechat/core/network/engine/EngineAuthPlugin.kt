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

/**
 * One credential, on one header, to one authority: the **portal's** bearer, in
 * `Proxy-Authorization`, which is what the reverse proxy's `forward-auth` reads.
 *
 * ## No more engine Basic (D-076)
 *
 * Until D-076 this plugin also put the engine's own `Authorization: Basic` on every request, from a
 * password the person typed into the settings. The edge now validates the bearer and then presents
 * the Basic itself (nginx, `hobbitton-agent-basic.inc` server-side), so the app holds no service
 * secret at all: `Authorization` is never set here. The earlier review had also found that Basic
 * riding the scheduler's client, towards a host that had no use for it — with it gone, that class
 * of leak has nothing left to leak.
 *
 * Authelia does not read `Authorization` on these hosts (`authn_strategies` names only
 * `HeaderProxyAuthorization` and `CookieSession`), and the engine never sees the bearer: the two
 * gates stay distinct.
 *
 * One measured caveat, worth repeating because it cost an afternoon on the server side:
 * `Proxy-Authorization` is a **hop-by-hop** header (RFC 7230 §6.1). A proxy is entitled to drop it,
 * and Caddy does. The deployment re-injects it explicitly on the authentication subrequest
 * (server-side D-031); should a future edge stop doing so, every request here comes back as a
 * redirect to the portal — which is what [isPortalRedirect] catches.
 *
 * ## One authority and nowhere else (finding M2, 26/09/2026)
 *
 * Ktor's `HttpRedirect` copies every header but `Authorization` to a redirect target — so an
 * engine, a scheduler or a proxy answering 302 towards a third-party host would hand it the
 * portal's bearer, a token that opens the engine *and* the scheduler and renews itself offline.
 * `KtorRedirectContractTest` pins that Ktor behaviour. The `State` phase attaches the bearer only
 * when the request addresses the client's own authority (scheme + host + port, see
 * [isSameServerAuthority]), and the `HttpSend` interceptor — the only place that sees a redirect
 * hop — strips it whenever a hop leaves that authority and puts it back when a chain comes home.
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
         * Which of the configured addresses this client speaks to — the only one its credential
         * may reach. The engine's client keeps the default; the scheduler's client picks
         * [EngineAccess.schedulerUrl]. A blank address matches nothing, so a client whose service
         * is not configured sends no credential at all rather than the bearer to whatever host
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
                context.applyPortalBearer(plugin.bearer())
            }

            scope.plugin(HttpSend).intercept { request ->
                val engine = plugin.access()
                if (engine == null || !isSameServerAuthority(request.url, plugin.authority(engine))) {
                    // A redirect hop off the authority — or a request that never was on it. The
                    // `State` phase ran once per call, before any hop; this is where the target
                    // of a hop is first visible.
                    request.headers.stripPortalBearer()
                    return@intercept execute(request)
                }
                if (!request.headers.contains(HttpHeaders.ProxyAuthorization)) {
                    // A chain that left the authority and came back (origin → object store →
                    // origin is an ordinary signed-URL shape). The strip above emptied the request
                    // on the way out, and a bare request to the engine reads as a login page.
                    request.applyPortalBearer(plugin.bearer())
                }
                val call = execute(request)

                val rejected = call.response.status == HttpStatusCode.Unauthorized ||
                    isPortalRedirect(call.response, engine.issuerUrl)
                if (!rejected) return@intercept call

                // Once, deliberately. A bearer refused twice means the portal session is gone, and
                // looping there turns « log in again » into a silent hammering of the server.
                if (request.attributes.getOrNull(RetryFlag) == true) return@intercept call

                val renewed = plugin.renew() ?: return@intercept call
                request.applyPortalBearer(renewed)
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

internal fun HttpRequestBuilder.applyPortalBearer(bearer: String?) {
    headers.stripPortalBearer()
    // Sending `Bearer null` would be worse than sending nothing: the proxy would reject a malformed
    // credential instead of treating the request as anonymous and saying so.
    if (bearer != null) {
        headers.append(HttpHeaders.ProxyAuthorization, "Bearer $bearer")
    }
}

/** The portal's bearer, and only that — nothing else on the request is this plugin's. */
internal fun HeadersBuilder.stripPortalBearer() {
    remove(HttpHeaders.ProxyAuthorization)
}
