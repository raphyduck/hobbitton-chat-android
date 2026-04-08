package com.garfiec.librechat.core.network.api.dto

import com.garfiec.librechat.core.model.UserFavorite
import kotlinx.serialization.Serializable

@Serializable
data class UpdateFavoritesRequest(
    val favorites: List<UserFavorite>,
)
