package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePart
import com.garfiec.librechat.core.network.di.librechatJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La fin d'une conversation gardée sur le téléphone : relue telle quelle, compressée, bornée en
 * nombre et en octets (les plus anciennes partent d'abord), effacée d'un bloc.
 */
class FileTranscriptCacheTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private fun cache(maxEntries: Int = 40, maxBytes: Long = 12L * 1024 * 1024, maxEntryBytes: Long = 2L * 1024 * 1024) =
        FileTranscriptCache(dossier.root, librechatJson, Dispatchers.Unconfined, maxEntries, maxBytes, maxEntryBytes)

    private fun transcript(n: Int, updated: Long? = 1L) = CachedTranscript(
        updated = updated,
        limit = 40,
        messages = (1..n).map { i ->
            EngineMessage(EngineMessageInfo(id = "m$i", role = "user"), listOf(EnginePart(id = "p$i", type = "text", text = "bonjour ".repeat(200))))
        },
    )

    @Test
    fun `une fin ecrite se relit telle quelle`() = runTest {
        val cache = cache()
        cache.write("ses_1", transcript(3, updated = 42))

        val lu = cache.read("ses_1")

        assertEquals(42L, lu?.updated)
        assertEquals(listOf("m1", "m2", "m3"), lu?.messages?.map { it.info.id })
        assertNull(cache.read("ses_absente"))
    }

    @Test
    fun `les plus anciennes partent quand le dossier deborde`() = runTest {
        val cache = cache(maxEntries = 2)
        cache.write("ses_1", transcript(1))
        Thread.sleep(20)
        cache.write("ses_2", transcript(1))
        Thread.sleep(20)
        cache.write("ses_3", transcript(1))

        assertNull(cache.read("ses_1"))
        assertEquals(1, cache.read("ses_2")?.messages?.size)
        assertEquals(1, cache.read("ses_3")?.messages?.size)
    }

    @Test
    fun `une fin trop lourde n'est pas gardee`() = runTest {
        val cache = cache(maxEntryBytes = 64)
        cache.write("ses_1", transcript(5))

        assertNull(cache.read("ses_1"))
        assertTrue(dossier.root.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `tout s'efface a la deconnexion`() = runTest {
        val cache = cache()
        cache.write("ses_1", transcript(1))
        cache.write("ses_2", transcript(1))

        cache.clear()

        assertNull(cache.read("ses_1"))
        assertNull(cache.read("ses_2"))
    }
}
