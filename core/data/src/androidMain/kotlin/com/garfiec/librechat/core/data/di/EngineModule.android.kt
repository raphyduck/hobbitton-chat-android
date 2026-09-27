package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.data.engine.EngineCallbackInbox
import com.garfiec.librechat.core.data.engine.EngineCallbackMailbox
import com.garfiec.librechat.core.data.engine.EngineRecentMissionsSource
import com.garfiec.librechat.core.data.engine.EngineSecureStore
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.EngineSignIn
import com.garfiec.librechat.core.data.engine.EngineSignInCoordinator
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.RecentMissionsSource
import com.garfiec.librechat.core.data.portal.AndroidWebCookieJar
import com.garfiec.librechat.core.data.portal.PortalSession
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.data.pricing.ModelPriceSource
import com.garfiec.librechat.core.data.repository.SignOutHook
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.client.CleartextGuardPlugin
import com.garfiec.librechat.core.network.engine.EngineEventParser
import com.garfiec.librechat.core.network.engine.EngineEventTransport
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.KtorEngineEventTransport
import com.garfiec.librechat.core.network.engine.PortalService
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.portalService
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The Agent engine's own graph: its HTTP client, its OAuth client, its stores.
 *
 * **Android only, for now, and deliberately.** The engine's secrets need a secure store, and on iOS
 * that means raw Keychain code (`SecItemAdd` and friends) that cannot be compiled or run from the
 * environment this was written in — only CI's macOS runner can. Shipping untested Keychain code to
 * find out is exactly the kind of guess that has cost this project its worst afternoons. The brief's
 * exit criterion for this phase is « Android, two tabs » (§9), so Android is where this lands.
 *
 * Reversing that is a known, bounded piece of work: an iOS `EngineSecureStore` over Keychain, an
 * `expect`/`actual` for it, and the Tasks module moved from `librechat.mobile.feature` to
 * `librechat.kmp.feature`. Recorded as D-034 in the server-side decision log.
 */
val engineModule: Module = module {

    single { EngineSecureStore(androidContext(), get(KoinQualifiers.IO)) }
    single<EngineTokenStore> { get<EngineSecureStore>().tokens }

    single {
        EngineSettingsStore(dataStore = get()).also { store ->
            // Warm the snapshot off the startup thread: the first engine request must not find an
            // empty base URL and fail as « unknown host » on a phone whose network is fine.
            get<CoroutineScope>(KoinQualifiers.ApplicationScope).launch { store.access() }
        }
    }

    /**
     * A **second** client, not the chat's.
     *
     * The chat's client is wired to LibreChat's base URL, its bearer, its refresh loop and its
     * account-switch barrier. Pointing it at the engine would send the chat's session to a host
     * that has no idea what to do with it, and not the one credential the engine's edge wants.
     */
    single(KoinQualifiers.Engine) { portalServiceClient(PortalService.ENGINE) }

    single { AgentEngineApi(get(KoinQualifiers.Engine)) }

    // The interactive chat's live feed: a byte transport over the engine's own client, framed and
    // reconnected by EngineStreamClient, each frame mapped by EngineEventParser. Wired here, with the
    // rest of the Android-only engine graph.
    single<EngineEventTransport> { KtorEngineEventTransport(get(KoinQualifiers.Engine)) }
    single { EngineEventParser(get()) }
    single { EngineStreamClient(get()) }

    /**
     * A **third** client, for the scheduler.
     *
     * Same portal, another host, the same bearer (its audience names both), from the same
     * [PortalSession]. Built by the same function as the engine's client (D-076): the two were
     * copies of each other, differing in the one line — the authority the bearer is scoped to —
     * that a copy is most likely to get wrong. The engine's Basic, which this client used to carry
     * for nothing, is gone with the password.
     *
     * Built even when no scheduler URL is set: the client is harmless without one (a blank
     * authority matches nothing), and the repository asks the settings before it calls anything.
     */
    single(KoinQualifiers.Scheduler) { portalServiceClient(PortalService.SCHEDULER) }

    single { SchedulerApi(client = get(KoinQualifiers.Scheduler), json = get()) }

    single { SchedulerRepository(api = get(), settings = get()) }

    /**
     * The price table's one supplier, bound where the scheduler is — Android only (D-034).
     *
     * The cache itself lives in the shared graph, because the chat holds it on every platform; here
     * is where it is told there is somewhere to fetch from. On iOS nothing binds this, the cache
     * receives null, and every picker renders without prices rather than crashing at first open.
     */
    single<ModelPriceSource> { get<SchedulerRepository>() }

    /**
     * The missions the drawer lists among the chats. Same arrangement as the price source above:
     * bound here, resolved with `getOrNull` by the drawer, so iOS gets a drawer of chats only rather
     * than a graph that fails to build.
     */
    single<RecentMissionsSource> { EngineRecentMissionsSource(api = get(), scheduler = get(), settings = get()) }

    /**
     * A **fourth** client, bare, for the portal (finding M1, 26/09/2026).
     *
     * The OAuth client talks to the **portal**, not the engine, and carries none of the engine's
     * client credentials: mixing them would put the bearer on every token request. Until
     * 26/09/2026 it rode the chat's client instead — the one with LibreChat's bearer, the gateway
     * headers, the account-switch barrier and the retry ladder. On a deployment where the portal
     * and LibreChat share a host, the chat's session bearer went along to the portal's token
     * endpoint, one authority's secret handed to another; and every token round waited behind the
     * chat account being ready, a coupling of two authorities the design says are separate.
     *
     * So: content negotiation, the cleartext guard, timeouts, and nothing that knows an identity.
     * No response validator either — `EngineTokenClient` reads the status itself, so a portal
     * refusal (`invalid_grant`) still arrives as `EngineGrantRefused` and not as a transient error
     * that would keep a dead session alive. Redirects stay on for discovery only: the two form
     * POSTs carry the verifier and the refresh token in their body, and Ktor never re-sends a
     * POST across a redirect.
     */
    single(KoinQualifiers.Portal) {
        HttpClient(get<HttpClientEngineFactory<*>>()) {
            install(ContentNegotiation) { json(get<Json>()) }
            install(CleartextGuardPlugin)
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
            }
        }
    }

    // The client id is `PORTAL_CLIENT_ID`, the constructor's default: one value, not a setting (D-076).
    single { EngineTokenClient(client = get(KoinQualifiers.Portal)) }

    /**
     * The one holder of the portal's tokens (D-076): the engine's client, the scheduler's client
     * and both sign-in paths — the login screen's and the Tasks tab's — read and write here.
     */
    single {
        PortalSession(
            store = get(),
            client = get(),
            endpoints = { get<EngineSettingsStore>().access()?.let { discoveredEndpoints(it.issuerUrl, get()) } },
            now = { kotlin.time.Clock.System.now().epochSeconds },
        )
    }

    /**
     * What an explicit sign-out owes the portal (D-076): its tokens and the web view's cookies on
     * the chat, portal, engine and scheduler hosts. Collected by `AuthRepositoryImpl` through
     * `getAll<SignOutHook>()`, so the upstream logout gains one call and no knowledge of the portal.
     */
    single {
        PortalSignOut(
            session = get(),
            access = { get<EngineSettingsStore>().access() },
            cookies = AndroidWebCookieJar(),
        )
    } bind SignOutHook::class

    /**
     * La boîte aux lettres du lien profond, en **singleton** — et c'est le point qui compte.
     *
     * `MainActivity` y dépose ce qu'Android lui délivre, le tour de connexion y relève. Deux
     * instances feraient deux boîtes : celle du dépôt jamais relevée, celle de l'attente jamais
     * remplie, et une connexion qui expire au bout de cinq minutes sans que rien n'explique
     * pourquoi. C'est aussi ce qui la distingue du socket qu'elle remplace, lui volontairement
     * fabriqué à chaque tour.
     */
    single { EngineCallbackMailbox() } binds arrayOf(
        EngineCallbackInbox::class,
        EngineCallbackDelivery::class,
    )

    /** Le premier jeton : le tour de portail complet. */
    single {
        EngineSignIn(
            access = { get<EngineSettingsStore>().access() },
            tokens = get(),
            sessions = get(),
            inbox = get(),
            endpoints = { issuerUrl -> discoveredEndpoints(issuerUrl, get()) },
        )
    }

    /**
     * Le tour du portail vit sur la portée APPLICATIVE, pas sur celle de l'écran.
     *
     * C'est le correctif du 24/08 : le `viewModelScope` meurt avec l'écran, et l'écran est
     * justement ce qui disparaît quand le navigateur passe devant. Le tour doit survivre au geste
     * qui l'a lancé, et son résultat attendre le retour.
     */
    single<EngineSignInLauncher> {
        EngineSignInCoordinator(
            portail = get(),
            portee = get(KoinQualifiers.ApplicationScope),
        )
    }

    /**
     * The same round trip, hosted by a web view instead of the browser (D-076): the login screen
     * runs it right after the chat's, in the web view that already holds the portal's session,
     * and the Tasks tab reuses it to sign in again.
     */
    single {
        PortalTasksSignIn(
            access = { get<EngineSettingsStore>().access() },
            launcher = get(),
            delivery = get(),
        )
    }
}

/**
 * One client of a service behind the portal, as [portalService] configures it: the same plugins,
 * the same timeouts, the bearer of the one [PortalSession], scoped to [service]'s own address.
 */
private fun Scope.portalServiceClient(service: PortalService): HttpClient {
    val settings = get<EngineSettingsStore>()
    val session = get<PortalSession>()
    val json = get<Json>()
    return HttpClient(get<HttpClientEngineFactory<*>>()) {
        portalService(
            service = service,
            jsonFormat = json,
            accessOf = { settings.access() },
            // Not a coroutine, hence the snapshot — warmed at startup and refreshed on every
            // suspend read. Same constraint as ServerUrlProvider's plain getter.
            snapshot = { settings.cachedAccess() },
            bearers = session,
        )
    }
}

/**
 * Discovery, cached for the lifetime of the process.
 *
 * Read from the issuer rather than assumed: the paths are Authelia's to change, and a hardcoded one
 * fails at the worst moment with a 404 that names nothing useful.
 */
private val discovered = mutableMapOf<String, EngineOAuthEndpoints>()

private suspend fun discoveredEndpoints(
    issuerUrl: String,
    client: EngineTokenClient,
): EngineOAuthEndpoints? {
    if (issuerUrl.isBlank()) return null
    discovered[issuerUrl]?.let { return it }
    return runCatching { client.discover(issuerUrl) }.getOrNull()?.also { discovered[issuerUrl] = it }
}
