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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.engine.EngineAddressField
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.web.PortalWebView
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.portal_addresses_hint
import com.garfiec.librechat.feature.auth.resources.portal_close
import com.garfiec.librechat.feature.auth.resources.portal_engine_url
import com.garfiec.librechat.feature.auth.resources.portal_invalid_url
import com.garfiec.librechat.feature.auth.resources.portal_issuer_url
import com.garfiec.librechat.feature.auth.resources.portal_problem_interrupted
import com.garfiec.librechat.feature.auth.resources.portal_problem_not_ready
import com.garfiec.librechat.feature.auth.resources.portal_problem_refused
import com.garfiec.librechat.feature.auth.resources.portal_problem_unreachable
import com.garfiec.librechat.feature.auth.resources.portal_scheduler_url
import com.garfiec.librechat.feature.auth.resources.portal_sign_in
import com.garfiec.librechat.feature.auth.resources.portal_sign_in_hint
import com.garfiec.librechat.feature.auth.resources.portal_step_preparing
import com.garfiec.librechat.feature.auth.resources.portal_title
import com.garfiec.librechat.feature.auth.resources.portal_unavailable
import com.garfiec.librechat.feature.auth.resources.portal_welcome
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginProblem
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginStep
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginUiState
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's only way in (D-077): the three addresses of the platform, then the portal.
 *
 * Replaces the LibreChat onboarding — server URL, login form, `/oauth/openid` — for the engine
 * shell. The addresses are the ones the Tasks tab's settings sheet edits ([EngineAddressField]);
 * the round trip is the one it runs, hosted in the app's own web view, full screen, over the form.
 */
@Composable
fun PortalSignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalLoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) currentOnSignedIn()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PortalAddressForm(
            state = state,
            onBaseUrl = viewModel::onBaseUrl,
            onSchedulerUrl = viewModel::onSchedulerUrl,
            onIssuerUrl = viewModel::onIssuerUrl,
            onStart = viewModel::start,
        )
        PortalLoginOverlay(
            state = state,
            onNavigation = viewModel::onNavigation,
            onClose = viewModel::cancel,
        )
    }
}

@Composable
private fun PortalAddressForm(
    state: PortalLoginUiState,
    onBaseUrl: (String) -> Unit,
    onSchedulerUrl: (String) -> Unit,
    onIssuerUrl: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Text(stringResource(Res.string.portal_welcome), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.portal_addresses_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (!state.available) {
                // iOS (D-034): no engine graph, no portal web view. Said, rather than a form that
                // would do nothing.
                Text(
                    text = stringResource(Res.string.portal_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("portal_unavailable"),
                )
            } else {
                val editable = state.step == PortalLoginStep.Idle
                AddressField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrl,
                    label = Res.string.portal_engine_url,
                    invalid = EngineAddressField.BASE_URL in state.invalid,
                    enabled = editable,
                    tag = "portal_engine_url",
                )
                AddressField(
                    value = state.schedulerUrl,
                    onValueChange = onSchedulerUrl,
                    label = Res.string.portal_scheduler_url,
                    invalid = EngineAddressField.SCHEDULER_URL in state.invalid,
                    enabled = editable,
                    tag = "portal_scheduler_url",
                )
                AddressField(
                    value = state.issuerUrl,
                    onValueChange = onIssuerUrl,
                    label = Res.string.portal_issuer_url,
                    invalid = EngineAddressField.ISSUER_URL in state.invalid,
                    enabled = editable,
                    tag = "portal_issuer_url",
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStart,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().testTag("login_portal"),
                ) {
                    Text(stringResource(Res.string.portal_sign_in))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.portal_sign_in_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.problem?.let { problem ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(problem.sentence()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("login_portal_error"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    invalid: Boolean,
    enabled: Boolean,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(Res.string.portal_invalid_url)) }
        } else {
            null
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(tag),
    )
}

/**
 * The web view of the sign-in, over the whole screen while a round trip is open.
 *
 * While the round trip is being prepared (addresses saved, request pushed to the portal) there is
 * no page yet: a spinner stands in. Closing it (button or back) abandons the sign-in.
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
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.portal_close))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.portal_title), style = MaterialTheme.typography.titleMedium)
                if (state.page == null) {
                    Text(
                        stringResource(Res.string.portal_step_preparing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.page == null) {
                CircularProgressIndicator()
            } else {
                PortalWebView(
                    url = state.page,
                    onNavigation = onNavigation,
                    modifier = Modifier.fillMaxSize().testTag("login_portal_web"),
                )
            }
        }
    }
}

private fun PortalLoginProblem.sentence() = when (this) {
    PortalLoginProblem.NOT_READY -> Res.string.portal_problem_not_ready
    PortalLoginProblem.UNREACHABLE -> Res.string.portal_problem_unreachable
    PortalLoginProblem.REFUSED -> Res.string.portal_problem_refused
    PortalLoginProblem.INTERRUPTED -> Res.string.portal_problem_interrupted
}
