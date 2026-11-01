package com.a207.runkk.watch.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.watch.cache.CachedTrack
import java.time.Duration

/**
 * 화면에 보이는 셀프·셀럽 상대 기록 하나(FR-258~FR-260). watch.ui는 표시 속성만 읽고 고른 값을 WatchBattleFlow에 그대로 돌려준다
 * (R75 결정, W30: watch.ui는 watch.cache·domain.battle 형식을 보지 않는다). [cached]는 대결을 열 때 WatchBattleFlow만 읽는다.
 */
data class RecordOpponent(internal val cached: CachedTrack) {
    /** 셀럽런 기록인가(아니면 셀프런 내 최고 기록). */
    val isCelebrity: Boolean get() = cached.record.kind == RecordBattleKind.CELEBRITY

    val distance: Distance get() = cached.record.distance

    /** 셀럽은 가린 표시명, 셀프런은 "내 기록"(서버 대결 시작 응답의 상대 이름과 같은 규칙). */
    val displayName: String get() = cached.record.displayName

    /** 상대 완주 시간. */
    val finishTime: Duration get() = cached.record.finishTime

    /** 셀럽 기록 출처(셀프런은 null). */
    val sourceLabel: String? get() = cached.record.sourceLabel
}
