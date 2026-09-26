package com.garfiec.librechat.core.network.client

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.util.AttributeKey

class AuthInterceptorPlugin private constructor(
    private val tokenManager: TokenManager,
    private val serverUrlProvider: ServerUrlProvider?,
) {
    class Config {
        lateinit var tokenManager: TokenManager

        /**
         * Resolves the configured LibreChat server base URL. The Authorization header is attached
         * ONLY to requests whose scheme, host and port match it — so a presigned absolute URL
         * fetch to a third-party CDN (S3/CloudFront, e.g. file download-url) never leaks the
         * session bearer token to that host, and neither does a same-host `http://` or other-port
         * URL the server hands back (finding M3, 26/09/2026).
         *
         * Fail-closed: with no provider and no [RequestIdentity] snapshot the server is unknown, and
         * a request whose server can't be established carries no bearer. Until 26/09/2026 a null
         * provider meant « attach everywhere »; the production clients all wire one, so only tests
         * ever relied on that, and they now say which server they mean.
         *
         * The rule itself lives in `HostScoping.kt` (`isSameServerAuthority`), shared with the
         * gateway-header plugin so the two can't drift onto different definitions of "same server".
         */
        var serverUrlProvider: ServerUrlProvider? = null
    }

    companion object : HttpClientPlugin<Config, AuthInterceptorPlugin> {
        override val key = AttributeKey<AuthInterceptorPlugin>("AuthInterceptor")
        private val RetryFlag = AttributeKey<Boolean>("AuthRetried")

        override fun prepare(block: Config.() -> Unit): AuthInterceptorPlugin {
            val config = Config().apply(block)
            return AuthInterceptorPlugin(config.tokenManager, config.serverUrlProvider)
        }

        override fun install(plugin: AuthInterceptorPlugin, scope: HttpClient) {
            // Endpoints that take no bearer and whose 401 is the endpoint's own verdict, not an
            // expired session — kept out of the refresh/session-expiry leg below. Shared with
            // SwitchBarrierPlugin; see AuthSkipPaths.kt.
            fun isSkipPath(url: URLBuilder): Boolean = isAuthSkipPath(url)

            // Attach token to outgoing requests. Runs at State, which is after
            // the SwitchBarrier/defaultRequest phases have applied the base URL —
            // so for the common relative-path call `context.url.host` is already
            // the base host, and for an absolute cross-host URL (e.g. a presigned
            // CDN download) it is that foreign host.
            //
            // When the SwitchBarrierPlugin is installed the request carries a
            // RequestIdentity snapshot: the bearer, its account, and the server
            // URL, all captured atomically. Read the bearer and host-scope from
            // that snapshot so a switch mid-request can't tear the (url, token)
            // pair. Without the barrier (refresh client / tests) fall back to the
            // live active account, preserving legacy behavior.
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val snapshot = context.attributes.getOrNull(RequestIdentityKey)
                val serverBaseUrl = snapshot?.baseUrl ?: plugin.serverUrlProvider?.getBaseUrl()
                if (!isSkipPath(context.url) && isSameServerAuthority(context.url, serverBaseUrl)) {
                    // Explicit branch (not `?:`): a snapshot whose bearer is null must attach
                    // nothing — a pending add-account probe before sign-in has no token yet, and
                    // falling through to the live cache would send the ACTIVE account's bearer to
                    // the arbitrary host being added.
                    val token = if (snapshot != null) snapshot.bearer else plugin.tokenManager.getAccessToken()
                    if (token != null) {
                        context.headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }

            // Intercept 401 responses at the HttpSend level for proper retry.
            scope.plugin(HttpSend).intercept { request ->
                val originalCall = execute(request)

                if (originalCall.response.status != HttpStatusCode.Unauthorized) {
                    return@intercept originalCall
                }

                if (isSkipPath(request.url)) {
                    return@intercept originalCall
                }

                // Scope the refresh-and-reattach exactly like the build-phase attach: never
                // refresh a token and re-send it to a foreign authority (a presigned CDN URL, a
                // same-host `http://` downgrade). For a non-server authority, pass the original 401
                // straight through with no token on the retry.
                val snapshot = request.attributes.getOrNull(RequestIdentityKey)
                val serverBaseUrl = snapshot?.baseUrl ?: plugin.serverUrlProvider?.getBaseUrl()
                if (!isSameServerAuthority(request.url, serverBaseUrl)) {
                    return@intercept originalCall
                }

                // A pending-identity request (add-account flow) has no keyed account to refresh, and
                // its auth failures belong to the add flow's UI: pass the 401 through without a
                // refresh and without the global session-expired signal, which would tear down the
                // *active* account's session over another account's failed sign-in.
                if (snapshot?.isPending == true) {
                    return@intercept originalCall
                }

                // Session-expiry emissions below are scoped to the snapshot's account: a straggler
                // request of a switched-away (retained, still-valid-in-roster) account must not tear
                // down the live account's session over its own dead credentials.
                val alreadyRetried = request.attributes.getOrNull(RetryFlag) == true
                if (alreadyRetried) {
                    Logger.w("Auth") { "401 after retry - session expired" }
                    plugin.tokenManager.emitSessionExpired(snapshot?.accountId)
                    return@intercept originalCall
                }

                Logger.d("Auth") { "401 received, attempting token refresh" }
                when (val refresh = plugin.tokenManager.refreshBearerFor(snapshot)) {
                    is BearerResult.Refreshed -> {
                        request.headers {
                            remove(HttpHeaders.Authorization)
                            append(HttpHeaders.Authorization, "Bearer ${refresh.token}")
                        }
                        request.attributes.put(RetryFlag, true)
                        execute(request)
                    }
                    BearerResult.Expired -> {
                        // Hard failure: the session is gone. Route the (snapshot's) account to re-auth.
                        Logger.w("Auth") { "Token refresh failed - session expired" }
                        plugin.tokenManager.emitSessionExpired(snapshot?.accountId)
                        originalCall
                    }
                    BearerResult.Transient -> {
                        // Recoverable failure (network/5xx/malformed/server false-negative). Do NOT
                        // emit session-expired — fail just this request and keep the user signed in so
                        // a later request (or relaunch) recovers instead of a spurious logout.
                        Logger.w("Auth") { "Token refresh transient failure - keeping session" }
                        originalCall
                    }
                }
            }
        }
    }
}
