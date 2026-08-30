package com.garfiec.librechat.core.data.datastore

enum class ChatFontSize {
    SMALL, MEDIUM, LARGE;

    companion object {
        fun fromString(value: String?): ChatFontSize = when (value) {
            "small" -> SMALL
            "large" -> LARGE
            else -> MEDIUM
        }
    }

    fun toStorageString(): String = when (this) {
        SMALL -> "small"
        MEDIUM -> "medium"
        LARGE -> "large"
    }

    /**
     * What every text style is multiplied by at this setting.
     *
     * Beside the enum rather than in the screens: the same three constants were written out in the
     * chat's Android screen, its iOS screen and the Tasks conversation's view model, so a fourth
     * surface reading the setting had to rediscover them — and three copies of a table is three
     * chances for one of them to be a tenth off.
     */
    val multiplier: Float
        get() = when (this) {
            SMALL -> 0.85f
            MEDIUM -> 1f
            LARGE -> 1.2f
        }
}
