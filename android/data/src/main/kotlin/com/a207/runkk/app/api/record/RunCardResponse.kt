package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

/** 공유 카드 값(서버 running.RunCardData). route는 코스지도 공유가 켜졌거나 본인일 때만 온다. */
@Serializable
internal data class RunCardResponse(
    val recordId: String,
    val cardKind: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val route: List<GeoPointDto> = emptyList(),
    val courseMapShared: Boolean = false
)
