package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant

/** 종료 요약(srs.md 1.3): 저장(CompletedRun) / 거부(잠금 중) / 저장 안 함(거리 0 또는 자유런 취소). */
sealed interface FinishOutcome {
    data class Saved(val run: CompletedRun) : FinishOutcome

    data object Rejected : FinishOutcome

    data object NotSaved : FinishOutcome
}
