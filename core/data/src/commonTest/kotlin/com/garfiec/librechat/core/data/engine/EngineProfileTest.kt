package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.data.portal.isPortalSignedIn
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A chat and a task, told apart (D-077) — the rules the drawer and the Tasks tab both sort by. */
class SessionKindClassificationTest {

    private fun message(agent: String? = null, provider: String? = null, role: String = "assistant") =
        EngineMessage(info = EngineMessageInfo(id = "m", role = role, agent = agent, providerId = provider))

    @Test
    fun `what this device recorded wins over everything`() {
        // A chat started here is a chat, even if its transcript were to say otherwise.
        val kind = classifySession(
            title = "veille — 2026-09-23T06:30+0200",
            recorded = EngineSessionKind.CHAT,
            messages = listOf(message(agent = "mission")),
        )

        assertEquals(EngineSessionKind.CHAT, kind)
    }

    @Test
    fun `a run the scheduler titled is a task without reading a message`() {
        assertEquals(EngineSessionKind.TASK, classifySession("veille — manuel", null, messages = null))
        assertEquals(
            EngineSessionKind.TASK,
            classifySession("veille — 2026-09-23T06:30+0200 reprise 2", null, messages = null),
        )
    }

    @Test
    fun `the agent written on the messages decides next`() {
        assertEquals(EngineSessionKind.CHAT, classifySession("Bonjour", null, listOf(message(agent = "chat"))))
        assertEquals(EngineSessionKind.TASK, classifySession("Bonjour", null, listOf(message(agent = "mission"))))
        // Any agent that is not the chat's is a task: `build`, a métier profile, a newer one.
        assertEquals(EngineSessionKind.TASK, classifySession("Bonjour", null, listOf(message(agent = "build"))))
    }

    @Test
    fun `the first message that names an agent is the one read`() {
        val kind = classifySession(
            title = "Bonjour",
            recorded = null,
            messages = listOf(message(agent = " "), message(agent = "chat"), message(agent = "mission")),
        )

        assertEquals(EngineSessionKind.CHAT, kind)
    }

    @Test
    fun `without an agent, the chat provider still marks a chat`() {
        assertEquals(
            EngineSessionKind.CHAT,
            classifySession("Bonjour", null, listOf(message(provider = CHAT_PROVIDER))),
        )
        assertEquals(
            EngineSessionKind.TASK,
            classifySession("Bonjour", null, listOf(message(provider = GATEWAY_PROVIDER))),
        )
    }

    @Test
    fun `a session that says nothing yet is unknown, not a task`() {
        assertNull(classifySession("Bonjour", null, messages = null))
        assertNull(classifySession("Bonjour", null, messages = emptyList()))
        assertNull(classifySession(null, null, listOf(message())))
    }
}

class ChatProfileTest {

    private val catalogue = ConnectorCatalogue(
        connecteurs = mapOf(
            "memoire" to ConnectorGrant(outils = listOf("memoire_lire"), chat = true),
            "planificateur" to ConnectorGrant(outils = listOf("planificateur_lister"), chat = true),
            "banque" to ConnectorGrant(outils = listOf("banque_soldes"), direct = false, chat = true),
            "shell" to ConnectorGrant(outils = listOf("bash")),
            "web" to ConnectorGrant(outils = listOf("webfetch"), chat = false),
        ),
    )

    @Test
    fun `a chat's perimeter is every connector open to a chat, and only those`() {
        assertEquals(listOf("banque", "memoire", "planificateur"), catalogue.chatPerimeter())
    }

    @Test
    fun `a scheduler that does not serve the flag opens nothing to a chat`() {
        val older = ConnectorCatalogue(connecteurs = mapOf("memoire" to ConnectorGrant(outils = listOf("memoire_lire"))))

        assertEquals(emptyList<String>(), older.chatPerimeter())
    }

    @Test
    fun `a chat's rules never allow a tool of a connector closed to chats`() {
        val allowed = permissionsFor(catalogue, catalogue.chatPerimeter())
            .filter { it.action == "allow" }
            .map { it.permission }

        assertTrue("memoire_lire" in allowed)
        assertTrue("bash" !in allowed)
        assertTrue("webfetch" !in allowed)
    }

    @Test
    fun `a chat's model always comes from the chat provider`() {
        assertEquals(
            EngineModelRef(providerId = CHAT_PROVIDER, modelId = "claude-sonnet-5"),
            EngineModelRef(providerId = GATEWAY_PROVIDER, modelId = "claude-sonnet-5").forChat(),
        )
        val already = EngineModelRef(providerId = CHAT_PROVIDER, modelId = "gpt-5.5")
        assertEquals(already, already.forChat())
    }

    @Test
    fun `each profile is offered its own providers`() {
        assertTrue(offersProvider(EngineProfile.CHAT, CHAT_PROVIDER))
        assertFalse(offersProvider(EngineProfile.CHAT, GATEWAY_PROVIDER))
        assertTrue(offersProvider(EngineProfile.TASK, GATEWAY_PROVIDER))
        assertFalse(offersProvider(EngineProfile.TASK, CHAT_PROVIDER))
    }

    @Test
    fun `the profiles name the server's agents`() {
        assertEquals("chat", EngineProfile.CHAT.agent)
        assertEquals("mission", EngineProfile.TASK.agent)
    }
}

class SessionKindStoreMergeTest {

    @Test
    fun `a newer verdict replaces an older one and moves to the end`() {
        val merged = cappedMerge(
            current = mapOf("a" to EngineSessionKind.TASK, "b" to EngineSessionKind.TASK),
            added = mapOf("a" to EngineSessionKind.CHAT),
            capacity = 10,
        )

        assertEquals<List<String>>(listOf("b", "a"), merged.keys.toList())
        assertEquals(EngineSessionKind.CHAT, merged["a"])
    }

    @Test
    fun `beyond the capacity the oldest goes`() {
        val merged = cappedMerge(
            current = mapOf("a" to EngineSessionKind.TASK, "b" to EngineSessionKind.CHAT),
            added = mapOf("c" to EngineSessionKind.CHAT),
            capacity = 2,
        )

        assertEquals<List<String>>(listOf("b", "c"), merged.keys.toList())
    }
}

/** The app's one signed-in state (D-077): three addresses and the portal's tokens. */
class PortalSignedInStateTest {

    private val access = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        schedulerUrl = "https://sched.example.com",
    )
    private val tokens = EngineTokens(accessToken = "a", refreshToken = "r", expiresAtEpochSeconds = 0)

    @Test
    fun `addresses and tokens are signed in`() {
        assertTrue(isPortalSignedIn(access, tokens))
    }

    @Test
    fun `no tokens is signed out, whatever the addresses`() {
        assertFalse(isPortalSignedIn(access, null))
    }

    @Test
    fun `tokens without the addresses are signed out`() {
        assertFalse(isPortalSignedIn(null, tokens))
        // The scheduler is where the portal hands the code back: without it there is no sign-in.
        assertFalse(isPortalSignedIn(access.copy(schedulerUrl = ""), tokens))
        assertFalse(isPortalSignedIn(access.copy(baseUrl = ""), tokens))
    }
}
