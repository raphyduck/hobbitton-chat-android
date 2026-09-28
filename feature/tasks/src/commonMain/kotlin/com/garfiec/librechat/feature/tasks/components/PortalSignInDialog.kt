package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.ui.web.PortalWebView
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in
import org.jetbrains.compose.resources.stringResource

/**
 * The portal, full screen, for signing the tasks in again (D-076).
 *
 * The web view is the login's own ([PortalWebView]), so its cookie jar is the one the single
 * sign-in filled: while the portal's session lasts, this is one consent click, not a password and
 * a second factor. Dismissing it — close button, back — cancels the round trip at once.
 */
@Composable
internal fun PortalSignInDialog(
    page: String,
    onNavigation: (String) -> Boolean,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.tasks_cancel))
                    }
                    Text(stringResource(Res.string.tasks_sign_in), style = MaterialTheme.typography.titleMedium)
                }
                PortalWebView(
                    url = page,
                    onNavigation = onNavigation,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
