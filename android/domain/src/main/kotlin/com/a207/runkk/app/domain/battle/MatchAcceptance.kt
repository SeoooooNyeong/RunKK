package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 매칭 요청·취소·조회 결과(서버 battle.MatchAcceptance). [defaultDistance]는 기본 대결 거리(마지막으로 요청한 거리, 처음이면 5km,
 * FR-109)다. 거부는 AppError.Rejected(사유 코드)로 온다. 배치 중 회원의 요청은 대기 없이 배치고사 대결을 만들고(FR-274, D-479)
 * [placementRound]에 판 번호(1~3), [battleId]에 만든 대결 ID를 담는다. 그 밖(확정 티어 대기, 조회·취소)은 둘 다 null이다.
 */
data class MatchAcceptance(
    val isWaiting: Boolean,
    val distance: Distance?,
    val defaultDistance: Distance? = null,
    val placementRound: Int? = null,
    val battleId: Long? = null
)
