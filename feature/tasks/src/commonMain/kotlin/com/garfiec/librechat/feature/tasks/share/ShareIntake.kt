package com.garfiec.librechat.feature.tasks.share

import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where a share goes (09/11/2026, Raphaël: « partager des fichiers ou des photos ou des vidéos à
 * l'application … sous forme d'une nouvelle session ou d'une session existante à choisir lors du
 * partage »): a conversation that does not exist yet, or one the person picked.
 */
sealed interface ShareTarget {
    data object NewConversation : ShareTarget

    data class Conversation(val sessionId: String) : ShareTarget
}

/**
 * What another app handed to Eva: files, photos and videos already staged as the composer's picker
 * stages them (a photo shrunk, a video or a large file for the deposit), and the text that came
 * with them, waiting for the conversation they go to. [target] is null until the person picks one.
 */
data class PendingShare(
    val attachments: List<StagedAttachment>,
    val text: String? = null,
    val target: ShareTarget? = null,
) {
    val isEmpty: Boolean get() = attachments.isEmpty() && text.isNullOrBlank()
}

/**
 * The share in flight, from the activity that received it to the conversation that takes it.
 *
 * The activity [offer]s it; the shell shows the chooser while [PendingShare.target] is null and
 * [aim]s it at the pick; the view model of the conversation that matches the target takes it
 * ([takeFor]), once: two screens of one conversation cannot both receive it, and a recomposition
 * never doubles it. Dismissing the chooser drops the share.
 */
class ShareIntake {
    private val _pending = MutableStateFlow<PendingShare?>(null)
    val pending: StateFlow<PendingShare?> = _pending.asStateFlow()

    /** A new share replaces one nobody took: the person moved on to another. */
    fun offer(share: PendingShare) {
        if (share.isEmpty) return
        _pending.value = share.copy(target = null)
    }

    fun aim(target: ShareTarget) {
        _pending.update { it?.copy(target = target) }
    }

    fun dismiss() {
        _pending.value = null
    }

    /**
     * The share aimed at the conversation [sessionId] (null: a conversation that does not exist
     * yet), taken; null when there is none or it is aimed elsewhere.
     */
    fun takeFor(sessionId: String?): PendingShare? {
        var taken: PendingShare? = null
        _pending.update { current ->
            taken = null
            if (current != null && current.target.matches(sessionId)) {
                taken = current
                null
            } else {
                current
            }
        }
        return taken
    }
}

private fun ShareTarget?.matches(sessionId: String?): Boolean = when (this) {
    null -> false
    ShareTarget.NewConversation -> sessionId == null
    is ShareTarget.Conversation -> this.sessionId == sessionId
}
