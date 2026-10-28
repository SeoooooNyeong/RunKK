package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class PinResponse(val postId: Long, val pinned: Boolean)
