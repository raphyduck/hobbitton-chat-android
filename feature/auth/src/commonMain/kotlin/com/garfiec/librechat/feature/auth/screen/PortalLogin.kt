package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.garfiec.librechat.feature.auth.resources.portal_brand
import com.garfiec.librechat.feature.auth.resources.portal_close
import com.garfiec.librechat.feature.auth.resources.portal_engine_url
import com.garfiec.librechat.feature.auth.resources.portal_invalid_url
import com.garfiec.librechat.feature.auth.resources.portal_issuer_url
import com.garfiec.librechat.feature.auth.resources.portal_other_platform
import com.garfiec.librechat.feature.auth.resources.portal_platform
import com.garfiec.librechat.feature.auth.resources.portal_problem_interrupted
import com.garfiec.librechat.feature.auth.resources.portal_problem_not_ready
import com.garfiec.librechat.feature.auth.resources.portal_problem_refused
import com.garfiec.librechat.feature.auth.resources.portal_problem_unreachable
import com.garfiec.librechat.feature.auth.resources.portal_scheduler_url
import com.garfiec.librechat.feature.auth.resources.portal_sign_in
import com.garfiec.librechat.feature.auth.resources.portal_sign_in_hint
import com.garfiec.librechat.feature.auth.resources.portal_step_preparing
import com.garfiec.librechat.feature.auth.resources.portal_tagline
import com.garfiec.librechat.feature.auth.resources.portal_title
import com.garfiec.librechat.feature.auth.resources.portal_unavailable
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginProblem
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginStep
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginUiState
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's only way in (D-077): the portal, on the platform the build was made for.
 *
 * Since lot 4 (10/10/2026) the screen is Claude's sign-in in spirit: the mark and the name in the
 * serif, one button, and nothing to type when the build names a platform (`platform.properties`,
 * [PortalLoginUiState.prefilled]). « Autre plateforme » unfolds the three addresses — engine,
 * scheduler, portal ([EngineAddressField]) — which a build without defaults shows from the start.
 * The round trip runs in the app's own web view, full screen, over the form.
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
        if (state.signedIn) {
            viewModel.consumeSignedIn()
            currentOnSignedIn()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PortalAddressForm(
            state = state,
            onBaseUrl = viewModel::onBaseUrl,
            onSchedulerUrl = viewModel::onSchedulerUrl,
            onIssuerUrl = viewModel::onIssuerUrl,
            onStart = viewModel::start,
            onShowAddresses = viewModel::showAddresses,
        )
        PortalLoginOverlay(
            state = state,
            onNavigation = viewModel::onNavigation,
            onClose = viewModel::cancel,
        )
    }
}

@Composable
internal fun PortalAddressForm(
    state: PortalLoginUiState,
    onBaseUrl: (String) -> Unit,
    onSchedulerUrl: (String) -> Unit,
    onIssuerUrl: (String) -> Unit,
    onStart: () -> Unit,
    onShowAddresses: () -> Unit = {},
) {
    // Centred while it fits, scrolling once the keyboard or the three fields make it taller than
    // the screen: a scrolling column cannot centre itself, a box can centre a scrolling column.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BowTie(modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(Res.string.portal_brand),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.portal_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            if (!state.available) {
                // No engine graph, no portal web view (D-034). Said, rather than a form that would
                // do nothing.
                Text(
                    text = stringResource(Res.string.portal_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("portal_unavailable"),
                )
            } else {
                SignInControls(
                    state = state,
                    onBaseUrl = onBaseUrl,
                    onSchedulerUrl = onSchedulerUrl,
                    onIssuerUrl = onIssuerUrl,
                    onStart = onStart,
                    onShowAddresses = onShowAddresses,
                )
            }
        }
    }
}

@Composable
private fun SignInControls(
    state: PortalLoginUiState,
    onBaseUrl: (String) -> Unit,
    onSchedulerUrl: (String) -> Unit,
    onIssuerUrl: (String) -> Unit,
    onStart: () -> Unit,
    onShowAddresses: () -> Unit,
) {
    val editable = state.step == PortalLoginStep.Idle
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.addressesShown) {
            Text(
                text = stringResource(Res.string.portal_addresses_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
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
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onStart,
            enabled = editable,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_portal"),
        ) {
            Text(stringResource(Res.string.portal_sign_in), style = MaterialTheme.typography.titleMedium)
        }
        if (state.prefilled && !state.addressesShown) {
            // Where the button leads, and the way out for someone whose platform is another.
            Spacer(Modifier.height(12.dp))
            hostOf(state.baseUrl)?.let { host ->
                Text(
                    text = stringResource(Res.string.portal_platform, host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = onShowAddresses,
                enabled = editable,
                modifier = Modifier.testTag("login_other_platform"),
            ) {
                Text(stringResource(Res.string.portal_other_platform))
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = stringResource(Res.string.portal_sign_in_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        state.problem?.let { problem ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(problem.sentence()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("login_portal_error"),
            )
        }
}
}

/**
 * The bow tie, Butler's mark, drawn in the accent: two wings meeting at a knot. The launcher icon's
 * shape, redrawn here because the icon is an Android drawable the shared code cannot see.
 */
@Composable
private fun BowTie(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val w = size.width
        val h = w * WING_HEIGHT
        val top = (size.height - h) / 2
        val knotWidth = w * KNOT_WIDTH
        val knotLeft = (w - knotWidth) / 2
        val knotRight = knotLeft + knotWidth
        val inset = h * WING_INSET
        val wings = Path().apply {
            moveTo(0f, top)
            lineTo(knotLeft, top + inset)
            lineTo(knotLeft, top + h - inset)
            lineTo(0f, top + h)
            close()
            moveTo(w, top)
            lineTo(knotRight, top + inset)
            lineTo(knotRight, top + h - inset)
            lineTo(w, top + h)
            close()
        }
        drawPath(wings, color)
        val knotHeight = h * KNOT_HEIGHT
        val knot = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        Offset(knotLeft - knotWidth * KNOT_OVERLAP, top + (h - knotHeight) / 2),
                        Offset(knotRight + knotWidth * KNOT_OVERLAP, top + (h + knotHeight) / 2),
                    ),
                    cornerRadius = CornerRadius(knotWidth * KNOT_RADIUS),
                ),
            )
        }
        drawPath(knot, color)
    }
}

private const val WING_HEIGHT = 0.58f
private const val WING_INSET = 0.22f
private const val KNOT_WIDTH = 0.2f
private const val KNOT_HEIGHT = 0.62f
private const val KNOT_OVERLAP = 0.15f
private const val KNOT_RADIUS = 0.3f

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
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(tag),
    )
}

/** « agent.example.com » out of « https://agent.example.com/ »: what the screen says of the platform. */
internal fun hostOf(url: String): String? = url
    .trim()
    .substringAfter("://", url.trim())
    .substringBefore('/')
    .takeIf { it.isNotBlank() }

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
