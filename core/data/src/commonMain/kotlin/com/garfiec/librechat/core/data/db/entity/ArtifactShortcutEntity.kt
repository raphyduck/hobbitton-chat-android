package com.garfiec.librechat.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A pinned-artifact snapshot. Has NO accountId column: a home-screen launcher icon keeps working
 * across an account *switch*, so this table is device-scoped and no read filters it.
 *
 * It is nonetheless emptied by `AccountDataPurger` when an account is removed (logout included):
 * the snapshot holds the artifact's full content and the viewer opens it without a session, so on
 * a shared device the next user could read it from the launcher (review C9, 26/09/2026). A pinned
 * icon then opens an empty viewer rather than someone else's document.
 */
@Entity(tableName = "artifact_shortcuts")
data class ArtifactShortcutEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "shortcut_label")
    val shortcutLabel: String,
    @ColumnInfo(name = "emoji")
    val emoji: String?,
    @ColumnInfo(name = "identifier")
    val identifier: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "language")
    val language: String?,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
