package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path

class RolesApi(
    private val client: HttpClient,
) {
    suspend fun getRole(roleName: String): UserRolePermissions =
        client.get {
            url { path("api/roles/$roleName") }
        }.body()
}
