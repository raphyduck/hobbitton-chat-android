package com.librechat.android.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path
import javax.inject.Inject

class SearchApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun checkSearchEnabled(): Boolean =
        client.get {
            url { path("api/search/enable") }
        }.body()
}
