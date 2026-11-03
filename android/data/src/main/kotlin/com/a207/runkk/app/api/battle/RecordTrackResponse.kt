package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 상대 기록 하나(서버 BattleDtos.RecordTrackResponse, contract-v2 2절). [opponentName]은 기록 대결 시작 응답과 같은 규칙(셀럽은 가린
 * 표시명)이고 [track]은 POST /battles/self·celebrity가 주는 것과 같은 구간 기록이다. 셀프런이면 [celebrityId]·[sourceLabel]·
 * [celebrityFigure]가 null이다. 서버의 estimated(균등 페이스로 만든 구간 기록인가)는 앱이 쓰지 않아 읽지 않는다.
 */
@Serializable
internal data class RecordTrackResponse(
    val celebrityId: Long? = null,
    val opponentName: String,
    val distanceMeters: Long,
    val finishSeconds: Long,
    val track: List<TrackPointDto> = emptyList(),
    val sourceLabel: String? = null,
    val celebrityFigure: String? = null
)
