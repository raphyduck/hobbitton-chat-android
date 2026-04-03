package com.librechat.android.core.network.api

import com.librechat.android.core.model.UserKey
import com.librechat.android.core.model.request.UpdateKeyRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

class KeysApi constructor(
    private val client: HttpClient,
) {
    suspend fun getKeyExpiry(): List<UserKey> =
        client.get {
            url { path("api/keys") }
        }.body()

    suspend fun updateKey(request: UpdateKeyRequest): UserKey =
        client.put {
            url { path("api/keys") }
            setBody(request)
        }.body()

    suspend fun deleteKey(name: String) {
        client.delete {
            url { path("api/keys/${name.encodeURLPathPart()}") }
        }
    }

    suspend fun deleteAllKeys() {
        client.delete {
            url { path("api/keys") }
            parameter("all", true)
        }
    }
}
