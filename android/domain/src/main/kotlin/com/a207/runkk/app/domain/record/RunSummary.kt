package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 기록 목록·상세 공용 요약 한 줄. [isValid]는 화면에 보이지 않고 달력 날짜 칸 거리만 거른다(D-279).
 * 목록 행에만 오는 값(v0.14, D-279·D-292): [battleType]은 대결 종류(대결이 아니면 null), [verdict]는 승패(보류면 null),
 * [isPending]은 판정 보류, [isNewRecord]는 신기록, [newRecords]는 신기록 종류, [routeShape]은 경로 모양(최대 24점, 없으면 빈 목록).
 * 옛 서버가 주지 않으면 기본값이다(D-289).
 */
data class RunSummary(
    val recordId: String,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val isValid: Boolean,
    val mode: RunMode,
    val cardKind: CardKind,
    val battleType: RecordBattleType? = null,
    val verdict: RecordVerdict? = null,
    val isPending: Boolean = false,
    val isNewRecord: Boolean = false,
    val newRecords: List<NewRecordMark> = emptyList(),
    val routeShape: List<ShapePoint> = emptyList()
)
