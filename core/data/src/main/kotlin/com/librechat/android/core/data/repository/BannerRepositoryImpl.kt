package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.Banner
import com.librechat.android.core.network.api.BannerApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BannerRepositoryImpl @Inject constructor(
    private val bannerApi: BannerApi,
) : BannerRepository {

    override suspend fun getBanners(): Result<List<Banner>> =
        safeApiCall { bannerApi.getBanners() }
}
