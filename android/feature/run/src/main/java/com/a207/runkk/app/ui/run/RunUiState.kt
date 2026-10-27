package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.PauseLimit
import com.a207.runkk.app.domain.run.RunProgress
import java.time.Duration

data class RunUiState(
    val phase: RunPhase = RunPhase.PREPARING,
    val countdown: Int = COUNTDOWN_SECONDS,
    val progress: RunProgress = RunProgress.INITIAL,
    val targetDistance: Distance? = null,
    val targetPace: Pace? = null,
    /** 목표가 오늘 계획을 따른 것인가(카운트다운 카드 "오늘 계획 그대로", 아니면 "지난번 설정 그대로"). */
    val isFromCoachPlan: Boolean = false,
    /** 지금 정지가 이어진 시간(B2-2 "정지한 지"). 정지 중이 아니면 null. 화면이 정지를 본 순간부터 센다. */
    val pausedFor: Duration? = null,
    val isConsentRequired: Boolean = false,
    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫았다. 다시 시도하면 다시 보인다. */
    val isConsentDialogDismissed: Boolean = false,
    /** 누름 안내("길게 눌러 종료"·"길게 눌러 해제"). 잠깐 보였다 사라진다. */
    val hint: UiText? = null,
    /** 새 1km 지점 배너("2km, 11:04, 평균 페이스 5:32"). */
    val splitBanner: UiText? = null,
    /** 취소(기록을 남기지 않는 종료)를 눌러 확인을 묻는 중. */
    val isDiscardConfirming: Boolean = false,
    val error: UiText? = null
) {
    /** 일시정지 중(PAUSED·PAUSED_LOCKED, FR-194). 진행값의 정지 여부를 그대로 보인다(D-157). */
    val isPaused: Boolean get() = progress.isPaused

    /** 목표까지 남은 거리. 목표가 없거나 넘었으면 null. */
    val remaining: Distance? get() = targetDistance?.takeIf { it > progress.distance }?.minus(progress.distance)

    /** 재동의 안내를 보일 때(시작 전, 권한·동의 없음, 닫지 않음). */
    val isConsentDialogVisible: Boolean get() = phase == RunPhase.PREPARING && isConsentRequired &&
        !isConsentDialogDismissed

    /** 취소 확인 대화상자를 보일 때(측정·정지 화면). 잠금·종료 중에는 숨긴다(FR-182). */
    val isDiscardDialogVisible: Boolean get() = isDiscardConfirming && phase == RunPhase.RUNNING

    /** 정지 자동 종료까지 남은 시간(B2-3). 정지가 예고 시점(55분)을 넘었을 때만 있다(FR-195). */
    val autoEndIn: Duration?
        get() = pausedFor?.takeIf { isPaused && it >= PauseLimit.WARN_AFTER }?.let {
            PauseLimit.END_AFTER.minus(it).coerceAtLeast(Duration.ZERO)
        }

    val targetProgress: Float? get() = targetDistance?.takeIf { it.meters > 0 }?.let {
        (
            progress.distance.meters.toFloat() /
                it.meters
            ).coerceIn(0f, 1f)
    }

    companion object {
        const val COUNTDOWN_SECONDS = 3
    }
}
