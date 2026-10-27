package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

/** POST /runs 응답(서버 RunController.UploadResponse). status는 STORED 또는 ALREADY_STORED, valid는 유효 러닝 여부다. */
@Serializable
internal data class UploadResponse(val recordId: String, val status: String, val valid: Boolean = true)
