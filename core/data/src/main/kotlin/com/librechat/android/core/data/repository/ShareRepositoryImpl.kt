package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.SharedLink
import com.librechat.android.core.model.response.ShareLinkCheckResponse
import com.librechat.android.core.model.response.SharedLinksResponse
import com.librechat.android.core.network.api.ShareApi
import com.librechat.android.core.network.client.ServerUrlProvider

class ShareRepositoryImpl(
    private val shareApi: ShareApi,
    private val serverUrlProvider: ServerUrlProvider,
) : ShareRepository {

    override suspend fun createShareLink(conversationId: String): Result<String> {
        return safeApiCall {
            val sharedLink = shareApi.createShareLink(conversationId)
            val shareId = sharedLink.shareId
                ?: throw IllegalStateException("Server returned no shareId")
            val baseUrl = serverUrlProvider.getBaseUrl().trimEnd('/')
            "$baseUrl/share/$shareId"
        }
    }

    override suspend fun getSharedLinks(): Result<List<SharedLink>> {
        return safeApiCall {
            shareApi.getSharedLinks().links
        }
    }

    override suspend fun getSharedLinksPaginated(cursor: String?): Result<SharedLinksResponse> {
        return safeApiCall {
            shareApi.getSharedLinks(cursor = cursor)
        }
    }

    override suspend fun checkShareLink(conversationId: String): Result<ShareLinkCheckResponse> {
        return safeApiCall {
            shareApi.checkShareLink(conversationId)
        }
    }

    override suspend fun toggleShareVisibility(shareId: String): Result<SharedLink> {
        return safeApiCall {
            shareApi.toggleShareVisibility(shareId)
        }
    }

    override suspend fun deleteShareLink(shareId: String): Result<Unit> {
        return safeApiCall {
            shareApi.deleteShareLink(shareId)
        }
    }
}
