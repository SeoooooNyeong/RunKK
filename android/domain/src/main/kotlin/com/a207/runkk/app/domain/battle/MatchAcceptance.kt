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
 * FR-109)다. 거부는 AppError.Rejected(사유 코드)로 온다.
 */
data class MatchAcceptance(val waiting: Boolean, val distance: Distance?, val defaultDistance: Distance? = null)
