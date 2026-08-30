package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.feature.tasks.EngineSignInProblem
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_error_authentication
import com.garfiec.librechat.feature.tasks.resources.tasks_error_authentication_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_not_found
import com.garfiec.librechat.feature.tasks.resources.tasks_error_not_found_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_permission
import com.garfiec.librechat.feature.tasks.resources.tasks_error_permission_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_server
import com.garfiec.librechat.feature.tasks.resources.tasks_error_server_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unknown
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unreachable
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unreachable_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_interrupted
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_missing_scope
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_no_callback_host
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_not_configured
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_refused
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in_unreachable

// The words for a failure, in one place. Both screens used to disagree by omission: the tab's
// full-screen explanation showed a title AND a hint, while the conversation — `hint()` being
// private to the other file — could only show the title, on the very same failure.

/** The sentence shown for a failure. One per cause, because the remedies differ. */
internal fun EngineFailureKind.title() = when (this) {
    EngineFailureKind.AUTHENTICATION -> Res.string.tasks_error_authentication
    EngineFailureKind.PERMISSION -> Res.string.tasks_error_permission
    EngineFailureKind.NOT_FOUND -> Res.string.tasks_error_not_found
    EngineFailureKind.UNREACHABLE -> Res.string.tasks_error_unreachable
    EngineFailureKind.SERVER -> Res.string.tasks_error_server
    EngineFailureKind.UNKNOWN -> Res.string.tasks_error_unknown
}

/** Why, and what to do about it — null when there is nothing more useful than the title. */
internal fun EngineFailureKind.hint() = when (this) {
    EngineFailureKind.AUTHENTICATION -> Res.string.tasks_error_authentication_hint
    EngineFailureKind.PERMISSION -> Res.string.tasks_error_permission_hint
    EngineFailureKind.NOT_FOUND -> Res.string.tasks_error_not_found_hint
    EngineFailureKind.UNREACHABLE -> Res.string.tasks_error_unreachable_hint
    EngineFailureKind.SERVER -> Res.string.tasks_error_server_hint
    EngineFailureKind.UNKNOWN -> null
}

/**
 * What a failed sign-in says, one sentence per cause.
 *
 * Separate from [EngineFailureKind.hint] because they answer different questions: that one explains
 * why the *engine* turned a request away, this one why the *portal* did — and only one of the six
 * cases here can be fixed by trying again.
 */
internal fun EngineSignInProblem.sentence() = when (this) {
    EngineSignInProblem.NOT_CONFIGURED -> Res.string.tasks_sign_in_not_configured
    EngineSignInProblem.NO_CALLBACK_HOST -> Res.string.tasks_sign_in_no_callback_host
    EngineSignInProblem.PORTAL_UNREACHABLE -> Res.string.tasks_sign_in_unreachable
    EngineSignInProblem.REFUSED -> Res.string.tasks_sign_in_refused
    EngineSignInProblem.INTERRUPTED -> Res.string.tasks_sign_in_interrupted
    EngineSignInProblem.MISSING_SCOPE -> Res.string.tasks_sign_in_missing_scope
}
