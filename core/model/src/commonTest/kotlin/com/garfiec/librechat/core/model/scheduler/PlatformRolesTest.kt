package com.garfiec.librechat.core.model.scheduler

import com.garfiec.librechat.core.model.engine.EngineModelRef
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The platform's roles (D-086): who thinks, who scouts, who is the savant, as `GET /roles` says. */
class PlatformRolesTest {

    private val roles = Json { ignoreUnknownKeys = true }.decodeFromString(
        PlatformRoles.serializer(),
        """{"roles": {"savant": {"en_service": "claude-fable-5-1", "modeles": ["claude-fable-5-1", "gpt-6-astra"]},
                      "penseur": {"en_service": "claude-opus-5-5", "modeles": ["claude-opus-5-5"]},
                      "lecteur": {"en_service": null, "modeles": []}},
            "pilotes": ["penseur", "eclaireur", "savant"], "pilote_par_defaut": "penseur",
            "sous_agents": {"outil": "task", "conversation": ["eclaireur"], "mission": ["eclaireur-mission"]},
            "fournisseur_conversations": "hobbitton-chat"}""",
    )

    @Test
    fun theThinkerAndTheSavantAreNamedOnTheConversationsProvider() {
        assertEquals(EngineModelRef("hobbitton-chat", "claude-opus-5-5"), roles.thinker)
        assertEquals(EngineModelRef("hobbitton-chat", "claude-fable-5-1"), roles.savant)
        assertNull(roles.conversationModel("lecteur"), "an empty role names no model")
        assertNull(roles.conversationModel("inconnu"))
        assertNull(PlatformRoles.NONE.thinker)
    }

    @Test
    fun aModelIsRecognisedAsInServiceWhateverItsProvider() {
        assertTrue(roles.isInService(PlatformRoles.THINKER, EngineModelRef("hobbitton-gateway", "claude-opus-5-5")))
        assertTrue(roles.isInService(PlatformRoles.THINKER, EngineModelRef("hobbitton-chat", "Claude-Opus-5-5")))
        assertFalse(roles.isInService(PlatformRoles.THINKER, EngineModelRef("hobbitton-chat", "claude-fable-5-1")))
        assertFalse(roles.isInService(PlatformRoles.THINKER, null))
    }

    @Test
    fun aQualifiedModelSplitsIntoProviderAndId() {
        assertEquals(EngineModelRef("hobbitton-chat", "deepseek-v4-pro"), "hobbitton-chat/deepseek-v4-pro".asModelRef())
        assertNull("deepseek-v4-pro".asModelRef())
        assertNull("hobbitton-chat/".asModelRef())
    }
}
