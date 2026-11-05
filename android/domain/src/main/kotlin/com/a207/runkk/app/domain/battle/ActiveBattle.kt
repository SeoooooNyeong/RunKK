package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import java.time.Instant

/**
 * 진행 중인 대결(GET /battles/active). 앱을 다시 켰는데 서버에 대결이 남아 있으면 그만두기로 끝내 새 대결 잠금을 푼다(배틀 QA 7번).
 * [type]은 앱이 모르는 종류면 null, [status]는 서버 Battle 상태 이름 그대로다. [opponentName]·[startAt]은 없으면 null이다.
 * [opponentGender]는 상대 성별(상대 그림을 고른다)이고 모르면 null이다. [celebrityFigure]는 셀럽 그림 키(셀럽 대결이 아니거나 없으면 null)다.
 * [placementRound]는 배치고사 대결이면 판 번호, 아니면 null이다(FR-274).
 */
data class ActiveBattle(
    val battleId: Long,
    val type: BattleHistory.Type?,
    val status: String,
    val distance: Distance,
    val opponentName: String?,
    val startAt: Instant?,
    val opponentGender: Gender? = null,
    val celebrityFigure: String? = null,
    val placementRound: Int? = null
)
