package com.a207.runkk.watch.cache

import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordTrack
import java.time.Instant

/** 받은 시각과 함께 워치에 둔 셀프·셀럽 상대 기록 하나(3.4, FR-260 "받은 시각을 함께 둔다"). */
data class CachedTrack(val record: RecordTrack, val fetchedAt: Instant) {
    /**
     * 진행용 구간 기록이 있어 바로 달릴 수 있는가. 셀럽런 목록은 구간 기록 없이 오고(W31) 고를 때 받으므로, 셀럽은 구간 기록이 대결
     * 거리에 닿을 때만 참이다(서버는 거리에 닿지 못하는 기록을 주지 않는다). 셀프런은 목록에 구간 기록이 함께 오므로 언제나 참이다.
     */
    val hasTrack: Boolean
        get() = record.kind == RecordBattleKind.SELF || record.track.arrivalTime(record.distance) != null
}
