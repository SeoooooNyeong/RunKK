package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

@Serializable
internal data class TrackPointDto(val elapsedSeconds: Long, val distanceMeters: Long)
