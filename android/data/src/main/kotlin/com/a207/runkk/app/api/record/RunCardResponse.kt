package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

/**
 * 공유 카드 값(서버 running.RunCardData). route는 코스지도 공유가 켜졌거나 본인일 때만 온다.
 * temperatureC·sky는 v0.14 앞면 날씨(서버 D-235), nickname은 앞면 서명 "[닉네임], 런크크"용 기록 주인 표시명이다(서버 BE2, 그 이전 서버면 없다).
 */
@Serializable
internal data class RunCardResponse(
    val recordId: String,
    val cardKind: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val averageHeartRate: Int? = null,
    val newRecords: List<String> = emptyList(),
    val route: List<GeoPointDto> = emptyList(),
    val courseMapShared: Boolean = false,
    val temperatureC: Int? = null,
    val sky: String? = null,
    val nickname: String? = null
)
