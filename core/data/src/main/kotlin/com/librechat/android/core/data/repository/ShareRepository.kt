package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.SharedLink
import com.librechat.android.core.model.response.ShareLinkCheckResponse
import com.librechat.android.core.model.response.SharedLinksResponse

interface ShareRepository {
    suspend fun createShareLink(conversationId: String): Result<String>
    suspend fun getSharedLinks(): Result<List<SharedLink>>
    suspend fun getSharedLinksPaginated(cursor: String? = null): Result<SharedLinksResponse>
    suspend fun checkShareLink(conversationId: String): Result<ShareLinkCheckResponse>
    suspend fun toggleShareVisibility(shareId: String): Result<SharedLink>
    suspend fun deleteShareLink(shareId: String): Result<Unit>
}
