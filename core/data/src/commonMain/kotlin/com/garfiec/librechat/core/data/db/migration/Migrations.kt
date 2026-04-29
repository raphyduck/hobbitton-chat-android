package com.garfiec.librechat.core.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Pre-#67 builds stored Conversation.endpoint and endpointType as the Kotlin
 * enum `.name` (e.g. "OPENAI"). #67's writer emits the wire-format SerialName
 * ("openAI"); the migration rewrites legacy rows in place so the read-side
 * shim in ConversationMapper can be deleted.
 *
 * Hardcoded pairs (not iterated over EModelEndpoint.entries) keep this a
 * frozen historical artifact — future enum edits do not change which legacy
 * strings get rewritten.
 *
 * Note on lossy AGENTS: pre-#67 ChatPayloadBuilder silently coerced any
 * unknown endpoint name (OpenRouter, Deepseek, xAI, …) to EModelEndpoint.AGENTS
 * before persistence. So a legacy row with `endpoint = "AGENTS"` may have
 * originated from any custom endpoint and the original name is unrecoverable.
 * Rewriting "AGENTS" -> "agents" preserves the same lossy outcome the
 * read-side shim already produced. This is by design.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "AZURE_OPENAI" to "azureOpenAI",
            "OPENAI" to "openAI",
            "GOOGLE" to "google",
            "ANTHROPIC" to "anthropic",
            "ASSISTANTS" to "assistants",
            "AZURE_ASSISTANTS" to "azureAssistants",
            "AGENTS" to "agents",
            "CUSTOM" to "custom",
            "BEDROCK" to "bedrock",
        ).forEach { (legacy, wire) ->
            connection.execSQL("UPDATE conversations SET endpoint = '$wire' WHERE endpoint = '$legacy'")
            connection.execSQL("UPDATE conversations SET endpointType = '$wire' WHERE endpointType = '$legacy'")
            connection.execSQL("UPDATE presets SET endpoint = '$wire' WHERE endpoint = '$legacy'")
        }
    }
}
