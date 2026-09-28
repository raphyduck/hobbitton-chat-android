package com.garfiec.librechat.shared.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.GlobalProfileEditor
import com.garfiec.librechat.core.model.chat.GlobalProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The instructions editor: what is typed, what is stored, and where a save stands. */
data class EngineInstructionsUiState(
    val loading: Boolean = true,
    /** Whether the instructions ride on every turn; off parks them without erasing them. */
    val enabled: Boolean = true,
    val instructions: String = "",
    /** What is stored, to tell an edit from a no-op. Null until read. */
    val stored: GlobalProfile? = null,
    val saving: Boolean = false,
    /** The save went through: the screen closes. */
    val saved: Boolean = false,
    /** The save did not go through: the screen stays, with what was typed. */
    val saveFailed: Boolean = false,
) {
    /** Something typed differs from what is stored — the only case where saving means anything. */
    val dirty: Boolean
        get() = stored != null && (enabled != stored.enabled || instructions != stored.instructions)
}

/**
 * The global instructions (D-077): the `system` that `EngineMissionRepository` sends on every chat
 * and task turn.
 *
 * Edited behind Enregistrer / Annuler rather than saved on every keystroke, as LibreChat's profile
 * form did: these words steer every conversation and every unattended mission at once, and half a
 * sentence must not become an instruction because the screen was left mid-edit.
 *
 * Only what does not depend on LibreChat is offered: the instructions and the switch. The MCP
 * servers the profile may still name were LibreChat's (its `ephemeralAgent.mcp`); they are kept
 * as stored, never edited or dropped from here.
 */
class EngineInstructionsViewModel(
    private val editor: GlobalProfileEditor,
) : ViewModel() {

    private val _state = MutableStateFlow(EngineInstructionsUiState())
    val state: StateFlow<EngineInstructionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = try {
                editor.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Instructions: the stored profile could not be read" }
                GlobalProfile()
            }
            _state.update {
                it.copy(
                    loading = false,
                    enabled = stored.enabled,
                    instructions = stored.instructions,
                    stored = stored,
                )
            }
        }
    }

    fun setInstructions(text: String) {
        _state.update { it.copy(instructions = text, saveFailed = false) }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled, saveFailed = false) }
    }

    /** Writes what is typed over what is stored, keeping every field this screen does not show. */
    fun save() {
        val current = _state.value
        val stored = current.stored ?: return
        if (current.saving) return
        if (!current.dirty) {
            _state.update { it.copy(saved = true) }
            return
        }
        val updated = stored.copy(enabled = current.enabled, instructions = current.instructions.trim())
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                editor.save(updated)
                _state.update { it.copy(saving = false, saved = true, stored = updated) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Instructions: the profile could not be saved" }
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
