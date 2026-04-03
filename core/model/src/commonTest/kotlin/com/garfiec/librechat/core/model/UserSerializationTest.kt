package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UserSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalUserRoundTrip() {
        val original = User(email = "test@example.com")
        val encoded = json.encodeToString(User.serializer(), original)
        val decoded = json.decodeFromString(User.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun fullyPopulatedUserRoundTrip() {
        val original = User(
            id = "user-001",
            mongoId = "65abc456",
            name = "Test User",
            username = "testuser",
            email = "test@example.com",
            emailVerified = true,
            avatar = "https://example.com/avatar.jpg",
            provider = "google",
            role = "ADMIN",
            twoFactorEnabled = true,
            termsAccepted = true,
            favorites = listOf(
                UserFavorite(agentId = "agent-1", model = "gpt-4o", endpoint = "openAI"),
                UserFavorite(model = "claude-3-opus"),
            ),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-03-28T12:00:00Z",
        )
        val encoded = json.encodeToString(User.serializer(), original)
        val decoded = json.decodeFromString(User.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun userDeserializesFromServerJson() {
        val serverJson = """
            {
                "id": "user-srv",
                "_id": "mongo-srv",
                "name": "Server User",
                "email": "srv@example.com",
                "emailVerified": false,
                "provider": "local",
                "role": "USER",
                "twoFactorEnabled": false,
                "termsAccepted": true,
                "favorites": [
                    {"agentId": "a1", "endpoint": "agents"}
                ],
                "extraField": "ignored"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(User.serializer(), serverJson)
        assertEquals("user-srv", decoded.id)
        assertEquals("mongo-srv", decoded.mongoId)
        assertEquals("Server User", decoded.name)
        assertEquals(1, decoded.favorites.size)
        assertEquals("a1", decoded.favorites[0].agentId)
    }

    @Test
    fun userFavoriteRoundTrip() {
        val original = UserFavorite(
            agentId = "agent-x",
            model = "gpt-4o-mini",
            endpoint = "openAI",
        )
        val encoded = json.encodeToString(UserFavorite.serializer(), original)
        val decoded = json.decodeFromString(UserFavorite.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun userDefaultValuesPreserved() {
        val user = User(email = "defaults@example.com")
        assertEquals(false, user.emailVerified)
        assertEquals("local", user.provider)
        assertEquals("USER", user.role)
        assertEquals(false, user.twoFactorEnabled)
        assertEquals(false, user.termsAccepted)
        assertEquals(emptyList(), user.favorites)
    }
}
