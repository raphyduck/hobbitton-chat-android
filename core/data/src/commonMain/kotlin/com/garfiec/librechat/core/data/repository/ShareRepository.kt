package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.ShareLinkCheckResponse
import com.garfiec.librechat.core.model.response.SharedLinksResponse

interface ShareRepository {
    suspend fun createShareLink(conversationId: String): Result<String>
    suspend fun getSharedLinks(): Result<List<SharedLink>>
    suspend fun getSharedLinksPaginated(cursor: String? = null): Result<SharedLinksResponse>
    suspend fun checkShareLink(conversationId: String): Result<ShareLinkCheckResponse>
    suspend fun toggleShareVisibility(shareId: String): Result<SharedLink>
    suspend fun deleteShareLink(shareId: String): Result<Unit>
}
