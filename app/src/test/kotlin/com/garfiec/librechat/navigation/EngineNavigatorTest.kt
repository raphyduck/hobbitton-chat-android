package com.garfiec.librechat.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import com.garfiec.librechat.feature.tasks.navigation.MissionChat
import com.garfiec.librechat.feature.tasks.navigation.TasksList
import com.garfiec.librechat.shared.engine.EngineAppSettings
import com.garfiec.librechat.shared.engine.EngineInstructions
import com.garfiec.librechat.shared.engine.EngineNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine shell's back stack (D-077): the chat is the root, everything else sits over it. */
class EngineNavigatorTest {

    private fun navigator(vararg keys: NavKey) = EngineNavigator(NavBackStack(*keys))

    @Test
    fun `a started chat replaces the blank one in place`() {
        val subject = navigator(EngineChat())

        subject.chatStarted("ses_1", "Bonjour")

        assertEquals(listOf<NavKey>(EngineChat("ses_1", "Bonjour")), subject.backStack.toList())
    }

    @Test
    fun `a chat opened from the drawer becomes the root`() {
        val subject = navigator(EngineChat("ses_1"), TasksList, MissionChat("ses_m"))

        subject.openChat("ses_2", "Autre")

        assertEquals(listOf<NavKey>(EngineChat("ses_2", "Autre")), subject.backStack.toList())
    }

    @Test
    fun `a new chat from a chat under way starts blank`() {
        val subject = navigator(EngineChat("ses_1", "Bonjour"))

        subject.newChat()

        assertEquals(listOf<NavKey>(EngineChat()), subject.backStack.toList())
    }

    @Test
    fun `the tasks are pushed over the chat, and unwound to rather than stacked`() {
        val subject = navigator(EngineChat("ses_1"))

        subject.openTasks()
        subject.openMission("ses_m", "Mission")
        subject.openTasks()

        assertEquals(listOf<NavKey>(EngineChat("ses_1"), TasksList), subject.backStack.toList())
    }

    @Test
    fun `back never pops the chat itself`() {
        val subject = navigator(EngineChat())

        subject.openSettings()
        subject.goBack()
        subject.goBack()

        assertEquals(listOf<NavKey>(EngineChat()), subject.backStack.toList())
    }

    @Test
    fun `settings are not stacked twice`() {
        val subject = navigator(EngineChat())

        subject.openSettings()
        subject.openSettings()

        assertEquals(listOf<NavKey>(EngineChat(), EngineAppSettings), subject.backStack.toList())
    }

    @Test
    fun `the instructions open over the settings, once, and back returns to them`() {
        val subject = navigator(EngineChat(), EngineAppSettings)

        subject.openInstructions()
        subject.openInstructions()
        assertEquals(listOf<NavKey>(EngineChat(), EngineAppSettings, EngineInstructions), subject.backStack.toList())

        subject.goBack()
        assertEquals(listOf<NavKey>(EngineChat(), EngineAppSettings), subject.backStack.toList())
    }
}
