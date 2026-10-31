package com.a207.runkk.app.ui.run

import com.a207.runkk.app.domain.record.Evaluation

/** 러닝 결과 화면의 한 번 처리 이동. */
sealed interface RunResultEvent {
    /** 이 러닝이 티어 평가를 일으켰다(서버 기록 상세 evaluation, D-284): 평가 화면을 한 번 연다. */
    data class NavigateEvaluation(val evaluation: Evaluation) : RunResultEvent
}
