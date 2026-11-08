package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 측정 중 안내를 판단해 낸다(RunTracker 협력 객체, D-95). 설정 사본은 이 클래스만 읽는다(진동·근접 알림 여부 포함).
 * CoachingJudge는 회마다 새로 만들어 앞 회의 지점 상태가 남지 않는다.
 */
class RunCoach
@Inject
constructor(
    private val guidanceOutput: GuidanceOutput,
    private val settings: SettingsRepository
) {
    @Volatile
    private var judge: CoachingJudge = CoachingJudge.forMode(RunMode.FREE)

    /** 새 회를 시작한다: 모드에 맞는 규칙 묶음을 새로 만든다. */
    fun begin(mode: RunMode) {
        judge = CoachingJudge.forMode(mode)
    }

    /** 1초 안내: GuidanceContext를 조립해 평가하고 낸다. 정지 중이면 평가하지 않는다(FR-049, D-93 (8)). */
    fun guide(progress: RunProgress, session: RunSession) {
        if (progress.isPaused) return
        val context =
            GuidanceContext(
                progress = progress,
                todayPlan = session.spec.todayPlan,
                gapMeters = session.gapMeters,
                birthYear = settings.birthYear(),
                guidanceSettings = settings.guidanceSettings()
            )
        judge.evaluate(context).forEach(guidanceOutput::play)
    }

    fun play(guidance: Guidance) {
        guidanceOutput.play(guidance)
    }

    /** 워치 근접 알림(FR-196): 설정 사본의 근접 알림 켜짐과 워치 연결·격차로 ProximityAlertRule.evaluate에 맡긴다(D-119, D-137). */
    fun proximityOf(gapMeters: Int?, isWatchConnected: Boolean): ProximityAlert? =
        ProximityAlertRule.evaluate(gapMeters, isProximityAlertOn(), isWatchConnected)

    fun isBattleVibrationOn(): Boolean = settings.guidanceSettings().isBattleVibrationOn

    fun isProximityAlertOn(): Boolean = settings.guidanceSettings().isProximityAlertOn
}
