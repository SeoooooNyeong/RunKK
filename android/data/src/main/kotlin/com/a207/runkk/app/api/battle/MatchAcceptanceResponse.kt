package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 매칭 요청·취소·조회 결과(서버 BattleDtos.MatchAcceptanceResponse). defaultDistanceMeters는 기본 대결 거리다. */
@Serializable
internal data class MatchAcceptanceResponse(
    val waiting: Boolean,
    val distanceMeters: Long? = null,
    val defaultDistanceMeters: Long? = null
)
