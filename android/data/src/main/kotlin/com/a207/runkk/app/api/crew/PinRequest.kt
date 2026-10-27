package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class PinRequest(val pinned: Boolean)
