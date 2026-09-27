package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.web.PortalWebView
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.portal_close
import com.garfiec.librechat.feature.auth.resources.portal_or_email
import com.garfiec.librechat.feature.auth.resources.portal_problem_no_session
import com.garfiec.librechat.feature.auth.resources.portal_problem_session_failed
import com.garfiec.librechat.feature.auth.resources.portal_sign_in
import com.garfiec.librechat.feature.auth.resources.portal_sign_in_hint
import com.garfiec.librechat.feature.auth.resources.portal_step_session
import com.garfiec.librechat.feature.auth.resources.portal_step_tasks
import com.garfiec.librechat.feature.auth.resources.portal_title
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginProblem
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginStep
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The portal as the login screen's primary way in (D-076): one button above the email form, which
 * stays below it as the fallback while the server still accepts it.
 */
@Composable
internal fun PortalLoginEntry(
    state: PortalLoginUiState,
    enabled: Boolean,
    showEmailDivider: Boolean,
    onStart: () -> Unit,
) {
    // One node at the top level: the caller lays this out as a single item of its column.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onStart,
            enabled = enabled && state.step == PortalLoginStep.Idle,
            modifier = Modifier.fillMaxWidth().testTag("login_portal"),
        ) {
            Text(state.label ?: stringResource(Res.string.portal_sign_in))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.portal_sign_in_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        state.problem?.let { problem ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.problemDetail ?: stringResource(problem.sentence()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("login_portal_error"),
            )
        }
        if (showEmailDivider) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.portal_or_email),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * The web view of the single sign-in, over the whole login screen while a round trip is open.
 *
 * One composable for all three steps, so the web view — and the page on it — survives from the
 * portal to the consent: the step only changes the line under the title, and, while the chat
 * session opens, a spinner over the page. Closing it (button or back) abandons the sign-in before
 * the chat is signed in, and skips the tasks after.
 */
@Composable
internal fun PortalLoginOverlay(
    state: PortalLoginUiState,
    onNavigation: (String) -> Boolean,
    onClose: () -> Unit,
) {
    if (state.step == PortalLoginStep.Idle) return
    PlatformBackHandler(enabled = true, onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            // The portal's form is typed into: the keyboard must shrink the page, not cover it.
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, enabled = state.step != PortalLoginStep.Session) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.portal_close))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.portal_title), style = MaterialTheme.typography.titleMedium)
                stepLine(state.step)?.let { line ->
                    Text(
                        stringResource(line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PortalWebView(
                url = state.page,
                onNavigation = onNavigation,
                modifier = Modifier.fillMaxSize().testTag("login_portal_web"),
            )
            if (state.step == PortalLoginStep.Session) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

private fun stepLine(step: PortalLoginStep) = when (step) {
    PortalLoginStep.Session -> Res.string.portal_step_session
    PortalLoginStep.Tasks -> Res.string.portal_step_tasks
    PortalLoginStep.Idle, PortalLoginStep.Portal -> null
}

private fun PortalLoginProblem.sentence() = when (this) {
    PortalLoginProblem.NO_SESSION -> Res.string.portal_problem_no_session
    PortalLoginProblem.SESSION_FAILED -> Res.string.portal_problem_session_failed
}

private const val SCRIM_ALPHA = 0.7f
