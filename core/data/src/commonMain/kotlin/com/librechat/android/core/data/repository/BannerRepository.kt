package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Banner

/** Fetches server-configured banners for display in the app. */
interface BannerRepository {
    suspend fun getBanners(): Result<List<Banner>>
}
