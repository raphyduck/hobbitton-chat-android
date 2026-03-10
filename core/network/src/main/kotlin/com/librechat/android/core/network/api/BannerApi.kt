package com.librechat.android.core.network.api

import com.librechat.android.core.model.Banner
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path
import javax.inject.Inject

class BannerApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun getBanners(): List<Banner> =
        client.get {
            url { path("api/banner") }
        }.body()
}
