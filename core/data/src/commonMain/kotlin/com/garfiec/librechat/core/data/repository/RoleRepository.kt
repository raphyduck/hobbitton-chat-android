package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import kotlinx.coroutines.flow.StateFlow

interface RoleRepository {
    /**
     * Latest permissions for the authenticated user's role, or null if not yet loaded.
     * Emits null briefly at startup before the DataStore cache prime completes.
     */
    val userPermissions: StateFlow<UserRolePermissions?>

    /**
     * Fetches `/api/roles/{currentUser.role}`, updates the StateFlow + DataStore on success.
     * On network failure with a cached value present, returns Result.Success(cached) so
     * gates can keep working offline. On network failure with no cache, returns Result.Error.
     */
    suspend fun fetchUserRole(): Result<UserRolePermissions>

    /** Wipes in-memory StateFlow and DataStore cache. Called on logout. */
    suspend fun clear()
}
