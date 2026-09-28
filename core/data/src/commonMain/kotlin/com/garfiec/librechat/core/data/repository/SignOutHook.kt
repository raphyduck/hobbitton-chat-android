package com.garfiec.librechat.core.data.repository

/**
 * Work an explicit sign-out owes beyond LibreChat's own session.
 *
 * Collected with `getAll()` into [AuthRepositoryImpl] and run at the end of `logout()`, inside the
 * same non-cancellable teardown — the hobbitton overlay binds one (`PortalSignOut`: the portal's
 * tokens and the web view's cookies, D-076) from `engineModule`, which is Android-only. On a
 * platform that binds none the list is empty and logout is exactly the upstream one.
 *
 * A hook must not throw into the teardown; one that does is logged and the others still run.
 */
fun interface SignOutHook {
    /**
     * [serverUrl] is the chat server the ended session belonged to, captured before the teardown —
     * by the time a hook runs, a surviving account may already have been promoted and the live
     * server URL be another one.
     */
    suspend fun onSignedOut(serverUrl: String?)
}
