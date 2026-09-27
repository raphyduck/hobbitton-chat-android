package com.garfiec.librechat.core.common.network

/**
 * Where plain `http://` is tolerated, and where it is not (M4 and F5, 26/09/2026).
 *
 * The app's one cleartext use case is a self-hosted server on the user's own network:
 * `http://192.168.1.20:3080`, `http://localhost:3080`, `http://nas.local`. Every other `http://`
 * address puts the password, both LibreChat tokens, the gateway headers and the engine's Basic on
 * the wire in the clear, over a path nobody controls. So `https://` is accepted anywhere and
 * `http://` only towards a host that can only be reached from inside a private network: loopback,
 * the RFC 1918 and CGNAT (RFC 6598) ranges, link-local, IPv6 unique-local, the mDNS and home
 * suffixes, and a single-label name — which no public DNS resolver will ever answer.
 *
 * **One rule, three enforcement points**, because none of them covers the others: the server URL
 * screen and the engine settings form refuse the address at entry; `CleartextGuardPlugin` (in
 * `:core:network`) refuses the request at send time, which is what catches an absolute `http://`
 * URL the server itself hands back (a download URL, an avatar) or a redirect towards one. Android's
 * `network_security_config` cannot express any of this — it takes domain names, not address
 * ranges — so its base policy stays permissive and this object is the effective one. iOS has no
 * ATS exception for these hosts either; the same policy applies through the shared clients.
 *
 * Pure Kotlin on purpose: the entry-side callers live in `commonMain` feature modules with no
 * Ktor, so the host is parsed by hand, and the parser tolerates exactly the shapes an address
 * field produces — an optional userinfo, an IPv6 literal in brackets, a port, a path.
 */
object CleartextPolicy {

    /**
     * True when [url] may be dialled as written: any `https://` URL, and an `http://` URL whose
     * host [isPrivateHost]. A URL with no scheme is not this object's problem and passes.
     */
    fun isPermitted(url: String): Boolean {
        val trimmed = url.trim()
        if (!isCleartextScheme(trimmed)) return true
        val host = hostOf(trimmed) ?: return false
        return isPrivateHost(host)
    }

    /** True when [host] — a name, an IPv4 or IPv6 literal — can only be reached from a private network. */
    fun isPrivateHost(host: String): Boolean {
        val candidate = host.trim().removeSurrounding("[", "]").substringBefore('%').trimEnd('.').lowercase()
        if (candidate.isEmpty()) return false
        if (candidate == "localhost" || candidate.endsWith(".localhost")) return true
        if (LOCAL_SUFFIXES.any { candidate.endsWith(it) }) return true
        ipv4Octets(candidate)?.let { return isPrivateIpv4(it) }
        if (':' in candidate) return isPrivateIpv6(candidate)
        return '.' !in candidate
    }

    /**
     * The host of [url], or null when there is none. Strips a userinfo, a port, a path, a query and
     * a fragment; keeps an IPv6 literal without its brackets.
     */
    fun hostOf(url: String): String? {
        val afterScheme = url.trim().substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
        return host.takeIf { it.isNotBlank() }
    }

    private fun isCleartextScheme(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("ws://", ignoreCase = true)

    private fun ipv4Octets(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            part.toInt()
        }
        return octets.takeIf { it.all { octet -> octet in 0..255 } }
    }

    private fun isPrivateIpv4(octets: List<Int>): Boolean {
        val (a, b) = octets
        return when (a) {
            127, 10 -> true
            172 -> b in 16..31
            192 -> b == 168
            100 -> b in 64..127
            169 -> b == 254
            else -> false
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        if (host == "::1") return true
        // An IPv4-mapped literal (`::ffff:192.168.1.1`) is judged as the IPv4 address it names.
        host.substringAfter("::ffff:", missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() }
            ?.let { mapped -> ipv4Octets(mapped)?.let { return isPrivateIpv4(it) } }
        val first = host.substringBefore(':')
        return first.startsWith("fc") || first.startsWith("fd") ||
            (first.length == 4 && first.startsWith("fe") && first[2] in "89ab")
    }

    /** Suffixes that resolve only on a local network: mDNS (RFC 6762), RFC 8375, and common router defaults. */
    private val LOCAL_SUFFIXES = listOf(".local", ".home.arpa", ".internal", ".lan", ".home")
}
