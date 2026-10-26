package com.garfiec.librechat.core.data.engine

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * La fin d'une conversation gardée sur le téléphone (26/10/2026) : écrite quand l'ouverture la lit,
 * relue telle quelle, et le moteur n'est relu que si la session a bougé depuis ou travaille.
 */
class EngineTranscriptCacheTest {

    private class CacheMemoire : TranscriptCache {
        val tails = mutableMapOf<String, CachedTranscript>()
        override suspend fun read(sessionId: String) = tails[sessionId]
        override suspend fun write(sessionId: String, transcript: CachedTranscript) {
            tails[sessionId] = transcript
        }
        override suspend fun clear() = tails.clear()
    }

    private val sent = mutableListOf<HttpRequestData>()
    private var updated = 1_000L
    private var status = "{}"

    private fun engine() = MockEngine { request ->
        sent += request
        val path = request.url.encodedPath
        val body = when {
            path == "/session/status" -> status
            path == "/session/ses_1" -> """{"id":"ses_1","title":"Bonjour","time":{"created":1,"updated":$updated}}"""
            path.endsWith("/message") -> """[{"info":{"id":"m1","role":"user"},"parts":[{"id":"p1","type":"text","text":"salut"}]},""" +
                """{"info":{"id":"m2","role":"assistant"},"parts":[{"id":"p2","type":"text","text":"bonjour"}]}]"""
            else -> "{}"
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun messageReads() = sent.count { it.url.encodedPath.endsWith("/message") }

    @Test
    fun `la fin lue a l'ouverture est gardee avec l'horloge de la session`() = runTest {
        val cache = CacheMemoire()
        val repository = testMissionRepository(engine(), transcripts = cache)

        repository.history("ses_1", limit = 40)

        val kept = assertNotNull(cache.tails["ses_1"])
        assertEquals(1_000L, kept.updated)
        assertEquals(40, kept.limit)
        assertEquals(listOf("m1", "m2"), kept.messages.map { it.info.id })
        val cached = assertNotNull(repository.cachedHistory("ses_1"))
        assertEquals(2, cached.messageCount)
        assertEquals(1_000L, cached.updated)
    }

    @Test
    fun `la transcription entiere n'est pas gardee`() = runTest {
        val cache = CacheMemoire()
        val repository = testMissionRepository(engine(), transcripts = cache)

        repository.history("ses_1", limit = null)

        assertNull(cache.tails["ses_1"])
    }

    @Test
    fun `une session qui n'a pas bouge n'est pas relue`() = runTest {
        val repository = testMissionRepository(engine(), transcripts = CacheMemoire())

        assertNull(repository.historyUnlessUpdatedAt("ses_1", 40, updatedAt = 1_000L))
        assertEquals(0, messageReads())
    }

    @Test
    fun `une session qui a bouge, ou qui travaille, est relue`() = runTest {
        val repository = testMissionRepository(engine(), transcripts = CacheMemoire())

        updated = 2_000L
        assertNotNull(repository.historyUnlessUpdatedAt("ses_1", 40, updatedAt = 1_000L))
        assertEquals(1, messageReads())

        updated = 1_000L
        status = """{"ses_1":{"type":"busy"}}"""
        assertNotNull(repository.historyUnlessUpdatedAt("ses_1", 40, updatedAt = 1_000L))
        assertEquals(2, messageReads())
    }

    @Test
    fun `sans horloge gardee, le moteur est relu`() = runTest {
        val repository = testMissionRepository(engine(), transcripts = CacheMemoire())

        assertNotNull(repository.historyUnlessUpdatedAt("ses_1", 40, updatedAt = null))
        assertEquals(1, messageReads())
    }
}
