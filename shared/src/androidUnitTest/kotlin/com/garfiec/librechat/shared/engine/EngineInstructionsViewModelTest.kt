package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.data.datastore.GlobalProfileEditor
import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * L'éditeur des instructions globales : ce qui est lu, ce qui est écrit, et ce qui ne l'est pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineInstructionsViewModelTest {

    /** Un profil en mémoire, qui compte ses écritures et peut refuser de les faire. */
    private class InMemoryEditor(var stored: GlobalProfile, var failing: Boolean = false) : GlobalProfileEditor {
        var saves = 0

        override suspend fun current(): GlobalProfile = stored

        override suspend fun save(profile: GlobalProfile) {
            if (failing) error("disque plein")
            saves++
            stored = profile
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `what is stored is what the editor opens on`() {
        val editor = InMemoryEditor(GlobalProfile(enabled = false, instructions = "Lis index.md."))

        val state = EngineInstructionsViewModel(editor).state.value

        assertThat(state.loading).isFalse()
        assertThat(state.enabled).isFalse()
        assertThat(state.instructions).isEqualTo("Lis index.md.")
        assertThat(state.dirty).isFalse()
    }

    @Test
    fun `saving writes the text and keeps the fields this screen does not show`() {
        val editor = InMemoryEditor(GlobalProfile(instructions = "Avant", mcpServers = setOf("memoire")))
        val vm = EngineInstructionsViewModel(editor)

        vm.setInstructions("  Réponds en français.  ")
        assertThat(vm.state.value.dirty).isTrue()
        vm.save()

        assertThat(editor.stored).isEqualTo(
            GlobalProfile(enabled = true, instructions = "Réponds en français.", mcpServers = setOf("memoire")),
        )
        assertThat(vm.state.value.saved).isTrue()
    }

    @Test
    fun `nothing changed, nothing written, and the screen still closes`() {
        val editor = InMemoryEditor(GlobalProfile(instructions = "Pareil"))
        val vm = EngineInstructionsViewModel(editor)

        vm.save()

        assertThat(editor.saves).isEqualTo(0)
        assertThat(vm.state.value.saved).isTrue()
    }

    @Test
    fun `switching off is a change worth saving`() {
        val editor = InMemoryEditor(GlobalProfile(instructions = "Garde-les"))
        val vm = EngineInstructionsViewModel(editor)

        vm.setEnabled(false)
        vm.save()

        assertThat(editor.stored.enabled).isFalse()
        assertThat(editor.stored.instructions).isEqualTo("Garde-les")
    }

    @Test
    fun `a failed save keeps what was typed and says so`() {
        val editor = InMemoryEditor(GlobalProfile(), failing = true)
        val vm = EngineInstructionsViewModel(editor)

        vm.setInstructions("Nouveau texte")
        vm.save()

        val state = vm.state.value
        assertThat(state.saveFailed).isTrue()
        assertThat(state.saved).isFalse()
        assertThat(state.saving).isFalse()
        assertThat(state.instructions).isEqualTo("Nouveau texte")

        vm.setInstructions("Nouveau texte, corrigé")
        assertThat(vm.state.value.saveFailed).isFalse()
    }
}
