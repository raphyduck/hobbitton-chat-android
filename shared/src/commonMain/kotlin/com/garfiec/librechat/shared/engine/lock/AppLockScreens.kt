package com.garfiec.librechat.shared.engine.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garfiec.librechat.core.data.lock.AppLock
import com.garfiec.librechat.core.data.lock.LockState
import com.garfiec.librechat.core.data.lock.UnlockResult
import com.garfiec.librechat.core.ui.components.EvaMark
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_lock_cancel
import com.garfiec.librechat.shared.resources.engine_lock_confirm_title
import com.garfiec.librechat.shared.resources.engine_lock_create_hint
import com.garfiec.librechat.shared.resources.engine_lock_create_title
import com.garfiec.librechat.shared.resources.engine_lock_current_title
import com.garfiec.librechat.shared.resources.engine_lock_delete
import com.garfiec.librechat.shared.resources.engine_lock_enter_code
import com.garfiec.librechat.shared.resources.engine_lock_fingerprint
import com.garfiec.librechat.shared.resources.engine_lock_fingerprint_changed
import com.garfiec.librechat.shared.resources.engine_lock_fingerprint_title
import com.garfiec.librechat.shared.resources.engine_lock_locked_title
import com.garfiec.librechat.shared.resources.engine_lock_mismatch
import com.garfiec.librechat.shared.resources.engine_lock_new_title
import com.garfiec.librechat.shared.resources.engine_lock_offer_hint
import com.garfiec.librechat.shared.resources.engine_lock_offer_later
import com.garfiec.librechat.shared.resources.engine_lock_offer_title
import com.garfiec.librechat.shared.resources.engine_lock_offer_yes
import com.garfiec.librechat.shared.resources.engine_lock_too_simple
import com.garfiec.librechat.shared.resources.engine_lock_use_code
import com.garfiec.librechat.shared.resources.engine_lock_wrong_code
import com.garfiec.librechat.shared.resources.engine_lock_wrong_current
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The lock over the signed-in app (28/10/2026): a code to create when there is none, the code (or
 * the fingerprint) to give when locked. The app below is composed only once it has been opened,
 * then kept under the lock so a return finds it where it was; while locked it is hidden from the
 * accessibility services too. [onSignOut] runs after the last wrong code.
 */
@Composable
fun AppLockGate(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLockViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var opened by remember { mutableStateOf(false) }
    if (state.lock == LockState.UNLOCKED) opened = true

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    Box(modifier.fillMaxSize()) {
        if (opened) {
            val hidden = state.lock != LockState.UNLOCKED
            Box(Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
        }
        when (state.lock) {
            LockState.CHECKING -> LockSurface {}
            LockState.UNSET -> CreateCodeScreen(onCreate = viewModel::create)
            LockState.LOCKED -> UnlockScreen(
                biometricsOn = state.biometrics,
                onCode = viewModel::unlock,
                onBiometricsUnlock = viewModel::unlockWithBiometrics,
                onBiometricsVoid = { viewModel.setBiometrics(false) },
                onSignOut = onSignOut,
            )
            LockState.UNLOCKED -> Unit
        }
    }
}

@Composable
private fun UnlockScreen(
    biometricsOn: Boolean,
    onCode: (String, (UnlockResult) -> Unit) -> Unit,
    onBiometricsUnlock: () -> Unit,
    onBiometricsVoid: () -> Unit,
    onSignOut: () -> Unit,
) {
    val biometrics = rememberBiometrics()
    val fingerprint = biometricsOn && biometrics?.available == true
    var wrong by remember { mutableStateOf<Int?>(null) }
    var voided by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.engine_lock_fingerprint_title)
    val useCode = stringResource(Res.string.engine_lock_use_code)
    val currentOnSignOut by rememberUpdatedState(onSignOut)

    val askFingerprint = {
        biometrics?.authenticate(title, useCode) { outcome ->
            when (outcome) {
                BiometricOutcome.SUCCESS -> onBiometricsUnlock()
                BiometricOutcome.INVALIDATED -> {
                    voided = true
                    onBiometricsVoid()
                }
                BiometricOutcome.CANCELLED -> Unit
            }
        }
        Unit
    }
    // The fingerprint is offered at once, as banking apps do; the code stays under it.
    LaunchedEffect(fingerprint) { if (fingerprint) askFingerprint() }

    val message = when {
        wrong != null -> stringResource(Res.string.engine_lock_wrong_code, wrong ?: 0)
        voided -> stringResource(Res.string.engine_lock_fingerprint_changed)
        else -> null
    }
    LockSurface {
        CodeEntry(
            title = stringResource(Res.string.engine_lock_locked_title),
            hint = stringResource(Res.string.engine_lock_enter_code),
            error = message,
            onFingerprint = if (fingerprint && !voided) askFingerprint else null,
            onComplete = { code ->
                onCode(code) { result ->
                    when (result) {
                        UnlockResult.Unlocked -> wrong = null
                        is UnlockResult.Wrong -> wrong = result.attemptsLeft
                        UnlockResult.TooManyAttempts -> currentOnSignOut()
                    }
                }
            },
        )
    }
}

private enum class CreateStep { ENTER, CONFIRM, OFFER }

@Composable
private fun CreateCodeScreen(onCreate: (code: String, biometrics: Boolean) -> Unit) {
    val biometrics = rememberBiometrics()
    var step by remember { mutableStateOf(CreateStep.ENTER) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<CreateError?>(null) }
    val title = stringResource(Res.string.engine_lock_fingerprint_title)
    val cancel = stringResource(Res.string.engine_lock_cancel)

    LockSurface {
        when (step) {
            CreateStep.ENTER, CreateStep.CONFIRM -> CodeEntry(
                title = stringResource(
                    if (step == CreateStep.ENTER) Res.string.engine_lock_create_title else Res.string.engine_lock_confirm_title,
                ),
                hint = stringResource(Res.string.engine_lock_create_hint),
                error = when (error) {
                    CreateError.MISMATCH -> stringResource(Res.string.engine_lock_mismatch)
                    CreateError.TOO_SIMPLE -> stringResource(Res.string.engine_lock_too_simple)
                    null -> null
                },
                onFingerprint = null,
                onComplete = { code ->
                    when {
                        step == CreateStep.ENTER && AppLock.isTooSimple(code) -> error = CreateError.TOO_SIMPLE
                        step == CreateStep.ENTER -> {
                            first = code
                            error = null
                            step = CreateStep.CONFIRM
                        }
                        code != first -> {
                            error = CreateError.MISMATCH
                            step = CreateStep.ENTER
                        }
                        biometrics?.available == true -> step = CreateStep.OFFER
                        else -> onCreate(code, false)
                    }
                },
            )
            CreateStep.OFFER -> FingerprintOffer(
                onYes = {
                    val keyMade = biometrics?.enroll() == true
                    if (!keyMade) {
                        onCreate(first, false)
                    } else {
                        biometrics?.authenticate(title, cancel) { outcome ->
                            if (outcome != BiometricOutcome.SUCCESS) biometrics.forget()
                            onCreate(first, outcome == BiometricOutcome.SUCCESS)
                        }
                    }
                },
                onLater = { onCreate(first, false) },
            )
        }
    }
}

private enum class CreateError { MISMATCH, TOO_SIMPLE }

@Composable
private fun FingerprintOffer(onYes: () -> Unit, onLater: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.engine_lock_offer_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.engine_lock_offer_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onYes, modifier = Modifier.fillMaxWidth().testTag("lock_offer_yes")) {
            Text(stringResource(Res.string.engine_lock_offer_yes))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.engine_lock_offer_later))
        }
    }
}

/** The opaque page every lock screen is drawn on; the back gesture does nothing under it. */
@Composable
internal fun LockSurface(content: @Composable () -> Unit) {
    PlatformBackHandler(enabled = true) {}
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
    }
}

/**
 * Six dots and a keypad of the app's own, as banking apps draw them: no system keyboard, so no
 * keyboard learns or suggests the code. [onComplete] gets the six digits, and the dots empty.
 */
@Composable
internal fun CodeEntry(
    title: String,
    hint: String?,
    error: String?,
    onFingerprint: (() -> Unit)?,
    onComplete: (String) -> Unit,
) {
    var digits by remember { mutableStateOf("") }
    val currentOnComplete by rememberUpdatedState(onComplete)
    val add = { digit: Char ->
        if (digits.length < AppLock.CODE_LENGTH) {
            digits += digit
            if (digits.length == AppLock.CODE_LENGTH) {
                val code = digits
                digits = ""
                currentOnComplete(code)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        EvaMark(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = error ?: hint.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 40.dp).testTag("lock_message"),
        )
        Spacer(Modifier.height(16.dp))
        Dots(filled = digits.length)
        Spacer(Modifier.weight(1f))
        Keypad(
            onDigit = add,
            onDelete = { digits = digits.dropLast(1) },
            onFingerprint = onFingerprint,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Dots(filled: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.semantics { contentDescription = "$filled / ${AppLock.CODE_LENGTH}" },
    ) {
        repeat(AppLock.CODE_LENGTH) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(14.dp)
                    .then(
                        if (on) {
                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onFingerprint: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> KeyButton(label = digit.toString(), onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onFingerprint != null) {
                val label = stringResource(Res.string.engine_lock_fingerprint)
                KeyButton(label = label, onClick = onFingerprint, testTag = "lock_fingerprint") {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Spacer(Modifier.size(KEY_SIZE))
            }
            KeyButton(label = "0", onClick = { onDigit('0') })
            val delete = stringResource(Res.string.engine_lock_delete)
            KeyButton(label = delete, onClick = onDelete, testTag = "lock_delete") {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = delete)
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    testTag: String = "lock_key_$label",
    icon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) icon() else Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

private val KEY_SIZE = 72.dp

private enum class ChangeStep { CURRENT, NEW, CONFIRM }

/**
 * Changing the code, from Settings: the current one first, then the new one twice. Full screen,
 * over the settings, on the lock's own keypad.
 */
@Composable
fun ChangeCodeDialog(
    onCheck: (String, (Boolean) -> Unit) -> Unit,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(ChangeStep.CURRENT) }
    var fresh by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<ChangeError?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                CodeEntry(
                    title = stringResource(
                        when (step) {
                            ChangeStep.CURRENT -> Res.string.engine_lock_current_title
                            ChangeStep.NEW -> Res.string.engine_lock_new_title
                            ChangeStep.CONFIRM -> Res.string.engine_lock_confirm_title
                        },
                    ),
                    hint = null,
                    error = when (error) {
                        ChangeError.WRONG -> stringResource(Res.string.engine_lock_wrong_current)
                        ChangeError.TOO_SIMPLE -> stringResource(Res.string.engine_lock_too_simple)
                        ChangeError.MISMATCH -> stringResource(Res.string.engine_lock_mismatch)
                        null -> null
                    },
                    onFingerprint = null,
                    onComplete = { code ->
                        when (step) {
                            ChangeStep.CURRENT -> onCheck(code) { right ->
                                error = if (right) null else ChangeError.WRONG
                                if (right) step = ChangeStep.NEW
                            }
                            ChangeStep.NEW -> if (AppLock.isTooSimple(code)) {
                                error = ChangeError.TOO_SIMPLE
                            } else {
                                fresh = code
                                error = null
                                step = ChangeStep.CONFIRM
                            }
                            ChangeStep.CONFIRM -> if (code == fresh) {
                                onChange(code)
                                onDismiss()
                            } else {
                                error = ChangeError.MISMATCH
                                step = ChangeStep.NEW
                            }
                        }
                    },
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(stringResource(Res.string.engine_lock_cancel))
                }
            }
        }
    }
}

private enum class ChangeError { WRONG, TOO_SIMPLE, MISMATCH }
