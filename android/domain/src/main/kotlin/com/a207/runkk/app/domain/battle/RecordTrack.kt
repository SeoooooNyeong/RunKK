package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import java.time.Duration

/**
 * 미리 받아 두는 셀프·셀럽 상대 기록 하나(GET /battles/record-tracks 응답 한 줄, D-377, v0.22 M75). 연결 없이 달리는 기록 대결
 * (OfflineRecordBattle)의 진행용 구간 기록([track])을 함께 가진다. [celebrityId]·[sourceLabel]·[celebrityFigure]는 셀프런이면 null이다.
 * [displayName]은 대결 시작 응답의 상대 이름과 같은 규칙(셀럽은 가린 표시명)이고 [finishTime]은 상대 완주 시간이다.
 */
data class RecordTrack(
    val kind: RecordBattleKind,
    val distance: Distance,
    val celebrityId: Long?,
    val displayName: String,
    val finishTime: Duration,
    val sourceLabel: String?,
    val celebrityFigure: String?,
    val track: OpponentTrack
)
