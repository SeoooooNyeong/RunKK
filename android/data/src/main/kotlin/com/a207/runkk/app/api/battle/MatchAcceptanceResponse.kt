package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 매칭 요청·취소·조회 결과(서버 BattleDtos.MatchAcceptanceResponse). defaultDistanceMeters는 기본 대결 거리다.
 * placementRound·battleId는 배치고사로 바로 만든 대결의 판 번호와 대결 ID이고 그 밖은 null이다(05-03 5.7.3.3 P2).
 */
@Serializable
internal data class MatchAcceptanceResponse(
    val waiting: Boolean,
    val distanceMeters: Long? = null,
    val defaultDistanceMeters: Long? = null,
    val placementRound: Int? = null,
    val battleId: Long? = null
)
