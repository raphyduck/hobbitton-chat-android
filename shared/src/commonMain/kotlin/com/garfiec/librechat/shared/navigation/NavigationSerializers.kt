package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.feature.agents.navigation.agentsSerializersModule
import com.garfiec.librechat.feature.auth.navigation.authSerializersModule
import com.garfiec.librechat.feature.chat.navigation.chatSerializersModule
import com.garfiec.librechat.feature.conversations.navigation.conversationsSerializersModule
import com.garfiec.librechat.feature.files.navigation.filesSerializersModule
import com.garfiec.librechat.feature.settings.navigation.settingsSerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus

/**
 * Combined [SerializersModule] for all navigation route types.
 * Registers polymorphic serializers for each feature module's sealed route hierarchy.
 * This will be used by Nav 3's SavedStateConfiguration for type-safe state saving.
 */
val navigationSerializersModule: SerializersModule =
    authSerializersModule +
        chatSerializersModule +
        conversationsSerializersModule +
        agentsSerializersModule +
        filesSerializersModule +
        settingsSerializersModule
