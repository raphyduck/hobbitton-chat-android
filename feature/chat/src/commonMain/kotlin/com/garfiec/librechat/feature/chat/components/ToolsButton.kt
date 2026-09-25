package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_tools_active
import org.jetbrains.compose.resources.stringResource

/**
 * The composer's « + » — attachments, model, tools — with the number of active tools on it.
 *
 * The count is what replaced the line of chips above the field (23/09/2026, Claude-style): each
 * enabled tool and MCP server used to get a chip of its own, three of which already took a full
 * row. The chips said *which*; the badge only says *how many*, and the sheet behind the « + »
 * says which. Nothing is lost that a tap cannot show.
 *
 * Shared by Android and iOS. Before, only Android had any sign at all (an unlabelled dot), so on
 * iOS a tool left enabled in an old conversation was invisible until the sheet was opened.
 */
@Composable
fun ToolsButton(
    activeTools: Int,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // Spoken with the count, so TalkBack/VoiceOver users get the same information as the badge.
    val spoken = if (activeTools > 0) {
        "$contentDescription, ${stringResource(Res.string.cd_tools_active, activeTools)}"
    } else {
        contentDescription
    }
    BadgedBox(
        badge = {
            if (activeTools > 0) {
                Badge { Text(activeTools.toString()) }
            }
        },
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(ChatInputDefaults.controlSize)
                .semantics {
                    this.contentDescription = spoken
                    role = Role.Button
                },
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}

/** The tools the sheet can toggle. MCP server names are counted separately. */
private val TOGGLEABLE_TOOLS = setOf(
    ToolConstants.WEB_SEARCH,
    ToolConstants.URL_CONTEXT,
    ToolConstants.CODE_INTERPRETER,
    ToolConstants.FILE_SEARCH,
    ToolConstants.MEMORY,
)

/**
 * How many tools the badge on the « + » should announce.
 *
 * Zero on the agents endpoint, whatever is selected: a saved agent runs its own tools and the
 * backend ignores the per-request ones there (`ChatInputGates.showEphemeralTools`). The selections
 * are kept — switching back to a model brings them back — only the count hides, as the chips did.
 *
 * An MCP server counts only if it is still offered: a selection outliving its server would
 * otherwise inflate a number nobody can reconcile with what the sheet lists.
 */
internal fun activeToolCount(
    enabledTools: Set<String>,
    selectedMcpServerNames: Set<String>,
    offeredMcpServerNames: Set<String>,
    showEphemeralTools: Boolean,
): Int {
    if (!showEphemeralTools) return 0
    return enabledTools.count { it in TOGGLEABLE_TOOLS } +
        selectedMcpServerNames.count { it in offeredMcpServerNames }
}

/**
 * The sheet's tool rows with the server's pinned tools first, in the server's order.
 *
 * `interface.defaultPinnedTools` (v0.8.7) used to become a row of one-tap chips on the composer.
 * The chips are gone with the rest of that row; the server's intent — « these ones matter here »
 * — is kept by putting them at the top of the list the « + » opens. Keys that are pinned but not
 * offered as a row are ignored, and the unpinned rows keep their usual order.
 */
internal fun pinnedFirst(rows: List<String>, pinned: List<String>): List<String> {
    val leading = pinned.filter { it in rows }.distinct()
    return leading + rows.filterNot { it in leading }
}
