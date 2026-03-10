package com.librechat.android.core.network.api

import com.librechat.android.core.model.SharedLink
import com.librechat.android.core.model.request.CreateShareRequest
import com.librechat.android.core.model.response.ShareLinkCheckResponse
import com.librechat.android.core.model.response.SharedLinksResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path
import javax.inject.Inject

class ShareApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun getSharedLinks(cursor: String? = null): SharedLinksResponse =
        client.get {
            url { path("api/share") }
            parameter("cursor", cursor)
            parameter("pageSize", 25)
            parameter("isPublic", true)
        }.body()

    suspend fun createShareLink(
        conversationId: String,
        targetMessageId: String? = null,
    ): SharedLink =
        client.post {
            url { path("api/share/$conversationId") }
            setBody(CreateShareRequest(targetMessageId = targetMessageId))
        }.body()

    suspend fun getShareLink(shareId: String): SharedLink =
        client.get {
            url { path("api/share/$shareId") }
        }.body()

    suspend fun checkShareLink(conversationId: String): ShareLinkCheckResponse =
        client.get {
            url { path("api/share/link/$conversationId") }
        }.body()

    suspend fun toggleShareVisibility(shareId: String): SharedLink =
        client.patch {
            url { path("api/share/$shareId") }
        }.body()

    suspend fun deleteShareLink(shareId: String) {
        client.delete {
            url { path("api/share/$shareId") }
        }
    }
}
