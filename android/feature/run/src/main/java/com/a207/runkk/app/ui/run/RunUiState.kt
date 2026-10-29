package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.domain.run.RunProgress

data class RunUiState(
    val phase: RunPhase = RunPhase.PREPARING,
    val countdown: Int = COUNTDOWN_SECONDS,
    val progress: RunProgress = RunProgress.INITIAL,
    /** 측정 중 경로선(RunTracker.routeLine, D-261). 지도 타일 없이 RouteCanvas가 그린다. */
    val route: RouteLine = RouteLine.EMPTY,
    val targetDistance: Distance? = null,
    val targetPace: Pace? = null,
    /** 목표가 오늘 계획을 따른 것인가(카운트다운 요약 "코치보이스"). */
    val isFromCoachPlan: Boolean = false,
    /** 러닝 음성 안내 설정(카운트다운 요약 "음성"). */
    val isVoiceOn: Boolean = true,
    /** 러닝 진동 안내 설정(카운트다운 요약 "진동"). */
    val isVibrationOn: Boolean = true,
    val isConsentRequired: Boolean = false,
    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫았다. 다시 시도하면 다시 보인다. */
    val isConsentDialogDismissed: Boolean = false,
    /** 누름 안내("길게 눌러 종료"). 잠깐 보였다 사라진다. */
    val hint: UiText? = null,
    /** 새 1km 지점 배너("2km, 11:04, 평균 페이스 5:32"). */
    val splitBanner: UiText? = null,
    /** 취소(기록을 남기지 않는 종료)를 눌러 확인을 묻는 중. */
    val isDiscardConfirming: Boolean = false,
    val error: UiText? = null
) {
    /** 일시정지 중(PAUSED, FR-194). 진행값의 정지 여부를 그대로 보인다. */
    val isPaused: Boolean get() = progress.isPaused

    /** 재동의 안내를 보일 때(시작 전, 권한·동의 없음, 닫지 않음). */
    val isConsentDialogVisible: Boolean get() = phase == RunPhase.PREPARING && isConsentRequired &&
        !isConsentDialogDismissed

    /** 취소 확인 대화상자를 보일 때(측정·정지 화면). 종료 중에는 숨긴다. */
    val isDiscardDialogVisible: Boolean get() = isDiscardConfirming && phase == RunPhase.RUNNING

    companion object {
        const val COUNTDOWN_SECONDS = 3
    }
}
