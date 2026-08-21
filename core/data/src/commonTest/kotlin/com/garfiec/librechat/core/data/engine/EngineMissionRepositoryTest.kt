package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.MissionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the tab is allowed to grant a mission, and what it is allowed to claim about one.
 */
class PermissionsForTest {

    @Test
    fun `the rule list opens with a deny-all`() {
        val rules = permissionsFor(listOf("memoire"), autonomous = true)

        // A profile is a ceiling; the checkboxes narrow it for this mission only. Starting from
        // « allow everything » and subtracting would turn a forgotten connector into a granted one.
        assertEquals("*", rules.first().permission)
        assertEquals("deny", rules.first().action)
    }

    @Test
    fun `a ticked connector opens exactly its own patterns`() {
        val rules = permissionsFor(listOf("memoire"), autonomous = true)
        val allowed = rules.filter { it.action == "allow" }.map { it.permission }

        assertTrue(allowed.containsAll(listOf("memoire_lire", "memoire_rechercher")))
        // Reading memory is not writing to it: the two are separate connectors on the server side
        // and must stay separate here, or ticking « memory » would hand out write access.
        assertTrue(allowed.none { it.startsWith("memoire_ecrire") })
    }

    @Test
    fun `shell is refused to an autonomous mission`() {
        val rules = permissionsFor(listOf("memoire", "shell"), autonomous = true)

        // Nobody is watching. An approval prompt nobody answers is not a safeguard — the mission
        // hangs until the watchdog kills it, and that is the *good* outcome.
        assertTrue(rules.none { it.permission == "bash" && it.action == "allow" })
    }

    @Test
    fun `shell is available to an interactive mission`() {
        val rules = permissionsFor(listOf("shell"), autonomous = false)

        assertTrue(rules.any { it.permission == "bash" && it.action == "allow" })
    }

    @Test
    fun `an unknown connector grants nothing rather than everything`() {
        val rules = permissionsFor(listOf("connecteur-invente"), autonomous = true)

        // A typo, a renamed connector, a newer server: none of them may end up widening access.
        assertEquals(1, rules.size)
        assertEquals("deny", rules.single().action)
    }

    @Test
    fun `no connectors at all is a mission that can only talk`() {
        val rules = permissionsFor(emptyList(), autonomous = true)

        assertEquals(listOf("*" to "deny"), rules.map { it.permission to it.action })
    }

    @Test
    fun `the same pattern reached twice is granted once`() {
        val rules = permissionsFor(listOf("memoire", "memoire"), autonomous = true)

        assertEquals(rules.size, rules.distinctBy { it.permission }.size)
    }
}

/** The state shown next to a mission, on the payloads the engine actually produces. */
class MissionStateFromEngineTest {

    @Test
    fun `a running mission is not judged on its messages`() {
        // It has not finished; there is nothing to conclude yet, and fetching its messages to decide
        // would download history for an answer that is already known.
        val state = judgeRunning()

        assertIs<MissionState.Running>(state)
    }

    private fun judgeRunning(): MissionState =
        com.garfiec.librechat.core.model.engine.judgeMission(
            status = com.garfiec.librechat.core.model.engine.EngineSessionStatus(type = "running"),
            messages = emptyList(),
        )
}
