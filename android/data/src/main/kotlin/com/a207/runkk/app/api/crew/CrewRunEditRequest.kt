package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/**
 * PATCH /crew-runs/{id} 요청(서버 CrewScheduleDtos.CrewRunEditRequest). 수정 화면의 네 칸을 모두 보낸다(빠진 칸을 "그대로"로 보지
 * 않는다). distanceMeters가 null이면 제한 없음이다.
 */
@Serializable
internal data class CrewRunEditRequest(
    val title: String,
    val body: String?,
    val distanceMeters: Long?,
    val repeat: String
)
