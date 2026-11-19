package com.axoo.features.posts.data

import com.axoo.features.posts.domain.RemoteMedia
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteMediaPage(val items: List<RemoteMedia>, val nextOffset: Int?)
