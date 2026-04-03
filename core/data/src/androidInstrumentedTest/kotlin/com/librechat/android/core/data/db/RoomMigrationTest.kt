package com.librechat.android.core.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates Room auto-migrations (v1→v2→v3) preserve all entity data
 * including complex JSON-serialized fields.
 *
 * Strategy: Create a v1 database, populate all 6 v1 entity types with
 * realistic data (including JSON columns), run migrations to v3,
 * verify every row and field survived intact.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LibreChatDatabase::class.java,
    )

    @Test
    fun migrateV1ToV3_allEntitiesPreserved() {
        // --- Create v1 database with all 6 entity types ---
        val db = helper.createDatabase(testDbName, 1)

        // 1. Conversation with JSON tags and modelParams
        db.insert(
            "conversations",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("conversationId", "conv-001")
                put("title", "Migration Test Chat")
                put("user", "user-001")
                put("endpoint", "openAI")
                put("endpointType", "custom")
                put("model", "gpt-4o")
                put("agentId", "agent-001")
                put("isArchived", 0)
                put("tags", """["important","work"]""")
                put("iconURL", "https://example.com/icon.png")
                put("greeting", "Hello!")
                put("modelParams", """{"temperature":0.7,"maxTokens":4096}""")
                put("createdAt", 1700000000000L)
                put("updatedAt", 1700001000000L)
                put("lastSyncedAt", 1700000500000L)
            },
        )

        // 2. Message with JSON content, files, attachments, feedback, metadata
        db.insert(
            "messages",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("messageId", "msg-001")
                put("conversationId", "conv-001")
                put("parentMessageId", "msg-000")
                put("sender", "Assistant")
                put("text", "Hello from migration test!")
                put("content", """[{"type":"text","text":"Hello"},{"type":"image_file","image_file":{"file_id":"f1"}}]""")
                put("isCreatedByUser", 0)
                put("model", "gpt-4o")
                put("endpoint", "openAI")
                put("iconURL", null as String?)
                put("unfinished", 0)
                put("error", 0)
                put("finishReason", "stop")
                put("tokenCount", 42)
                put("feedback", """{"rating":"thumbsUp","comment":"Great response"}""")
                put("files", """[{"file_id":"f1","filename":"test.png","type":"image/png"}]""")
                put("attachments", """[{"type":"file","url":"https://example.com/doc.pdf"}]""")
                put("metadata", """{"plugins":[],"finish_reason":"stop"}""")
                put("createdAt", 1700000100000L)
                put("updatedAt", 1700000200000L)
            },
        )

        // 3. File entity
        db.insert(
            "files",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("fileId", "file-001")
                put("user", "user-001")
                put("conversationId", "conv-001")
                put("messageId", "msg-001")
                put("filename", "screenshot.png")
                put("filepath", "/uploads/user-001/screenshot.png")
                put("type", "image/png")
                put("bytes", 102400L)
                put("source", "local")
                put("width", 1920)
                put("height", 1080)
                put("createdAt", 1700000100000L)
                put("updatedAt", 1700000100000L)
            },
        )

        // 4. Agent with JSON tools and conversationStarters
        db.insert(
            "agents",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("id", "agent-001")
                put("name", "Code Helper")
                put("description", "Helps with code")
                put("avatar", """{"filepath":"/images/agent.png","source":"local"}""")
                put("provider", "openAI")
                put("model", "gpt-4o")
                put("category", "coding")
                put("authorName", "test-user")
                put("isPromoted", 1)
                put("conversationStarters", """["Write a function","Debug this code","Explain this pattern"]""")
                put("tools", """["code_interpreter","file_search","dalle"]""")
                put("updatedAt", 1700000000000L)
            },
        )

        // 5. Preset with JSON params
        // Note: "order" is a SQL reserved word, so use execSQL with backtick-quoted column
        db.execSQL(
            """INSERT INTO presets (presetId, title, endpoint, model, isDefault, `order`, params, createdAt, updatedAt)
               VALUES ('preset-001', 'Creative Writing', 'openAI', 'gpt-4o', 1, 0,
                       '{"temperature":0.9,"topP":0.95,"maxTokens":8192}', 1700000000000, 1700000000000)""",
        )

        // 6. Conversation tag
        db.insert(
            "conversation_tags",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("tag", "important")
                put("user", "user-001")
                put("description", "Important conversations")
                put("count", 5)
                put("position", 0)
                put("createdAt", 1700000000000L)
                put("updatedAt", 1700000000000L)
            },
        )

        db.close()

        // --- Run migrations v1 → v2 → v3 ---
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true)

        // --- Verify all data survived ---

        // Conversations
        migratedDb.query("SELECT * FROM conversations WHERE conversationId = 'conv-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Migration Test Chat")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("endpoint"))).isEqualTo("openAI")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("tags"))).isEqualTo("""["important","work"]""")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("modelParams"))).isEqualTo("""{"temperature":0.7,"maxTokens":4096}""")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))).isEqualTo(1700000000000L)
        }

        // Messages (with all JSON fields)
        migratedDb.query("SELECT * FROM messages WHERE messageId = 'msg-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("text"))).isEqualTo("Hello from migration test!")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("content"))).contains("image_file")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("feedback"))).contains("thumbsUp")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("files"))).contains("test.png")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("attachments"))).contains("doc.pdf")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("metadata"))).contains("finish_reason")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("tokenCount"))).isEqualTo(42)
        }

        // Files
        migratedDb.query("SELECT * FROM files WHERE fileId = 'file-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("filename"))).isEqualTo("screenshot.png")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("width"))).isEqualTo(1920)
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("bytes"))).isEqualTo(102400L)
        }

        // Agents (with JSON fields)
        migratedDb.query("SELECT * FROM agents WHERE id = 'agent-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("name"))).isEqualTo("Code Helper")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("avatar"))).contains("filepath")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("tools"))).contains("code_interpreter")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("conversationStarters"))).contains("Debug this code")
        }

        // Presets (with JSON params)
        migratedDb.query("SELECT * FROM presets WHERE presetId = 'preset-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Creative Writing")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("params"))).contains("temperature")
        }

        // Conversation tags
        migratedDb.query("SELECT * FROM conversation_tags WHERE tag = 'important'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("count"))).isEqualTo(5)
        }

        // v1→v2 added drafts table — verify it exists and is writable
        migratedDb.execSQL(
            "INSERT INTO drafts (conversation_id, text, updated_at) VALUES ('conv-001', 'Draft text', 1700002000000)",
        )
        migratedDb.query("SELECT * FROM drafts WHERE conversation_id = 'conv-001'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("text"))).isEqualTo("Draft text")
        }

        migratedDb.close()
    }

    @Test
    fun migrateV1ToV3_viaRoomApi_entitiesReadable() {
        // Create and populate a v1 database
        val db = helper.createDatabase(testDbName, 1)
        db.insert(
            "conversations",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("conversationId", "conv-api-test")
                put("title", "Room API Test")
                put("user", "user-001")
                put("isArchived", 0)
                put("tags", "[]")
                put("createdAt", 1700000000000L)
                put("updatedAt", 1700000000000L)
                put("lastSyncedAt", 0L)
            },
        )
        db.close()

        // Open with Room (auto-runs migrations)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(
            context,
            LibreChatDatabase::class.java,
            testDbName,
        ).build()

        // Verify we can read the migrated data via DAO
        val conversation = kotlinx.coroutines.runBlocking {
            roomDb.conversationDao().getById("conv-api-test")
        }
        assertThat(conversation).isNotNull()
        assertThat(conversation!!.title).isEqualTo("Room API Test")

        roomDb.close()
    }
}
