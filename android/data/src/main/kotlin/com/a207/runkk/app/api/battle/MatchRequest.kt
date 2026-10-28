package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchRequest(val distanceMeters: Long)
