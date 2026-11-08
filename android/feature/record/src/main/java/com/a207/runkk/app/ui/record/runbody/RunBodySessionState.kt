package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.domain.runbody.RunBodyResult

/**
 * 러닝별 탭의 고른 기록 분석 칸(FR-266). 피그마 G-17 대기, G-13 분석 중, G-19 실패, G-13·G-21·G-31 결과다.
 * 분석 작업이 없는 기록(404 RUNBODY_NOT_FOUND)은 [NotAnalyzable]로 G-31과 같은 "분석할 데이터가 부족해요"를 보인다.
 */
sealed interface RunBodySessionState {
    data object Loading : RunBodySessionState

    /** 작업은 있고 ai 제출 전(G-17). */
    data object Pending : RunBodySessionState

    /** 제출함, 결과 대기(G-13 분석 중). */
    data object Analyzing : RunBodySessionState

    /** 분석 실패이거나 상태를 읽지 못했다(G-19). "상태 다시 확인"으로 다시 읽는다. */
    data object Failed : RunBodySessionState

    data object NotAnalyzable : RunBodySessionState

    /** 출생연도가 없어 분석하지 못했다. 입력 안내와 프로필 편집 단추를 보이고, 돌아오면 다시 읽는다(MR !91 e2e 1). */
    data object NeedsBirthYear : RunBodySessionState

    data class Done(val result: RunBodyResult) : RunBodySessionState
}
