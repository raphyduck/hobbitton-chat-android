package com.garfiec.librechat.core.data.portal

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.repository.SignOutHook
import com.garfiec.librechat.core.network.engine.EngineAccess
import io.ktor.http.Url

/**
 * What signing out owes the portal (D-076): its tokens, and the web view's cookies on every host
 * the single login went through.
 *
 * One login now opens the chat *and* the tasks — so one logout must close both. Before, the
 * portal's tokens were never cleared at all, and the web view kept the portal's session cookie:
 * the next person to pick up the phone would find the Tasks tab still signed in, and the login
 * screen would walk straight through the portal without asking for a password.
 *
 * The cookie jar is the web view's, and it is shared by the whole process; only the hosts this
 * login touched are expired — the chat server, the portal, the engine and the scheduler — rather
 * than every cookie of every server the person has ever added.
 */
class PortalSignOut(
    private val session: PortalSession,
    private val access: suspend () -> EngineAccess?,
    private val cookies: WebCookieJar,
) : SignOutHook {

    override suspend fun onSignedOut(serverUrl: String?) {
        session.forget()
        val origins = portalOrigins(serverUrl, access())
        cookies.expire(origins)
        Logger.i("Portal") { "Signed out of the portal: tokens dropped, cookies expired on ${origins.size} origin(s)" }
    }
}

/**
 * The web view's cookie jar, as far as signing out needs it. An interface so the rule of *which*
 * cookies go ([portalOrigins], [expiringCookies]) is tested in common code, and only the two calls
 * into `CookieManager` stay on the platform.
 */
interface WebCookieJar {
    /** Expires every cookie the jar would send to each of [origins]. Best effort, never throws. */
    suspend fun expire(origins: List<String>)
}

/**
 * The origins the single login went through: the chat server, the portal, the engine and the
 * scheduler. Blank, schemeless or unparseable addresses are dropped; duplicates collapse (a chat
 * and a portal on one host are one origin).
 */
fun portalOrigins(serverUrl: String?, access: EngineAccess?): List<String> =
    listOfNotNull(serverUrl, access?.issuerUrl, access?.baseUrl, access?.schedulerUrl)
        .mapNotNull(::originOf)
        .distinct()

private fun originOf(address: String): String? {
    val trimmed = address.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true)) {
        return null
    }
    val url = runCatching { Url(trimmed) }.getOrNull() ?: return null
    if (url.host.isBlank()) return null
    val defaultPort = url.port == url.protocol.defaultPort
    return "${url.protocol.name}://${url.host.lowercase()}" + if (defaultPort) "" else ":${url.port}"
}

/**
 * The `Set-Cookie` values that expire every cookie named in [cookieHeader] — what a jar hands back
 * for an origin, `name=value; name2=value2` — on [host].
 *
 * A jar reports names and values, not where a cookie was scoped, and a cookie is only replaced by
 * one with the same name, domain and path. So each name is expired **host-only and on every parent
 * domain** down to two labels: the portal's session cookie is set on the registrable domain
 * (`hobbitton.at`, shared by the four hosts), LibreChat's are host-only. A variant that matches no
 * cookie is a no-op. `Path=/` is the path every one of them uses (Express's default, Authelia's).
 * `Secure` is added for an `https` origin, where a jar refuses to let a non-secure write replace a
 * secure cookie.
 */
fun expiringCookies(cookieHeader: String?, host: String, secure: Boolean): List<String> {
    val names = cookieHeader.orEmpty()
        .split(';')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (names.isEmpty()) return emptyList()

    val suffix = "; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT" + if (secure) "; Secure" else ""
    val domains = parentDomains(host)
    return names.flatMap { name ->
        listOf("$name=$suffix") + domains.map { domain -> "$name=$suffix; Domain=$domain" }
    }
}

/**
 * `auth.hobbitton.at` → `auth.hobbitton.at`, `hobbitton.at`. Never a single label (`at`): no jar
 * accepts a cookie there, and a public suffix one level up (`co.uk`) is refused just the same, so
 * trying it costs one ignored write. An address — IPv4, IPv6, `localhost` — has no parent domains.
 */
internal fun parentDomains(host: String): List<String> {
    val clean = host.trim().lowercase().removePrefix("[").removeSuffix("]")
    if (clean.isEmpty() || ':' in clean || clean.all { it.isDigit() || it == '.' }) return emptyList()
    val labels = clean.split('.').filter { it.isNotEmpty() }
    if (labels.size < 2) return emptyList()
    return (0..labels.size - 2).map { from -> labels.drop(from).joinToString(".") }
}
