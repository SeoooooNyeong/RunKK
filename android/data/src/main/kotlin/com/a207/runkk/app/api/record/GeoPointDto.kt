package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class GeoPointDto(val latitude: Double, val longitude: Double)
