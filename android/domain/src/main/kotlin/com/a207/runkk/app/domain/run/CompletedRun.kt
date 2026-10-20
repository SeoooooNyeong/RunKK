package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant

/**
 * 확정된 러닝 한 회(기기 보관 형태, D-54). Room 엔티티(data.store)와 서버 DTO(api.run)는 각 데이터 모듈의 매퍼가 만든다.
 * RunSession이 domain.run 형식만으로 만든다(design-review.md M32).
 */
data class CompletedRun(
    val recordId: String,
    val mode: RunMode,
    val cardKind: CardKind,
    val battleId: Long?,
    val crewRunPostId: Long?,
    val startedAt: Instant,
    val elapsed: Duration,
    val distance: Distance,
    val route: List<RoutePoint>,
    val unstable: List<UnstableSegment>,
    val heartRates: List<HeartRateSample>,
    val uploadState: UploadState = UploadState.NOT_SENT,
    val savedAt: Instant
) {
    /** 위치 동의 철회 파기 지시(FR-026): 경로 좌표를 지운 사본. */
    fun purgeRoute(): CompletedRun = copy(route = emptyList())

    /** 심박 동의 철회 파기 지시: 심박을 지운 사본. */
    fun purgeHeartRates(): CompletedRun = copy(heartRates = emptyList())
}

/** 보관 기록 전송 상태. */
enum class UploadState {
    NOT_SENT,
    SENDING
}

/** 종료 요약(srs.md 1.3): 저장(CompletedRun) / 거부(잠금 중) / 저장 안 함(거리 0). */
sealed interface FinishOutcome {
    data class Saved(val run: CompletedRun) : FinishOutcome

    data object Rejected : FinishOutcome

    data object NotSaved : FinishOutcome
}
