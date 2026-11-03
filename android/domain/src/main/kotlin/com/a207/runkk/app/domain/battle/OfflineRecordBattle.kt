package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance

/**
 * 서버 연결 없이 워치에서만 진행하는 기록 대결 한 회(D-377, 사용자 결정 W18). 미리 받은 상대 기록(RecordTrack)으로 기록 대결 진행도
 * (FR-098)를 돌리고, 기록은 자유런으로 확정해 보낸다(서버 대결 이력·리워드 없음). [celebrityId]는 셀럽런만 있다.
 */
data class OfflineRecordBattle(
    val kind: RecordBattleKind,
    val distance: Distance,
    val celebrityId: Long?,
    val track: OpponentTrack
)
