package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 나와의 싸움·유명인 대결 시작 정보(POST /battles/self·celebrity 응답, design-review.md M34).
 * cardKind는 SELF_RUN 또는 CELEB_RUN이다. [celebrityFigure]는 셀럽 그림 키(서버 celebrityFigure, 셀프런·없으면 null)다.
 */
data class RecordBattleStart(
    val battleId: Long,
    val opponentName: String,
    val distance: Distance,
    val track: OpponentTrack,
    val cardKind: CardKind,
    val celebrityFigure: String? = null,
    /** 상대 모습(셀프런은 그 기록의 나, [임시:D-성-252]). 셀럽·옛 서버면 null이다. */
    val opponentLook: RunnerLook? = null
)
