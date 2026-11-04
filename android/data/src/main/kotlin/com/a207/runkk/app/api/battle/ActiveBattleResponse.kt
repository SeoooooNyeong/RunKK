package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 진행 중인 대결(GET /battles/active 200 본문). type·status는 서버 enum 이름 그대로, opponentName·startAt은 없으면 null이다.
 * opponentGender는 상대 성별(MALE·FEMALE, 해당 없거나 옛 서버면 null)이다. celebrityFigure는 셀럽 그림 키(lee·hwang·son, 셀럽 대결이
 * 아니거나 옛 서버면 null)다. placementRound는 배치고사 대결의 판 번호(아니면 null, 05-03 5.7.3.3 P5)다.
 */
@Serializable
internal data class ActiveBattleResponse(
    val battleId: Long,
    val type: String,
    val status: String,
    val distanceMeters: Long,
    val opponentName: String? = null,
    val startAt: String? = null,
    val opponentGender: String? = null,
    val celebrityFigure: String? = null,
    val placementRound: Int? = null
)
