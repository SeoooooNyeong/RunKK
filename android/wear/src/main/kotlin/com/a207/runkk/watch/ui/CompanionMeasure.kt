package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunProgress
import java.time.Duration
import java.time.Instant

/**
 * 폰 러닝 동행 표시 상태를 워치 단독 측정 화면 상태로 옮긴다(W35, 피그마 FR∞·FR3·BR1·SR3·FP∞). 화면은 [WatchRunScreen]·[WatchPausedScreen]을
 * 그대로 쓰고 조작만 폰 요청(WatchActions.onCompanion…)으로 잇는다.
 * - 일시정지할 수 있는 자유런([WatchUiState.isPausable])은 자유런 측정 화면이고, 멈추면 일시정지 화면(계속·종료)이다.
 * - 그 밖(대결·셀프·셀럽·크루런)은 대결 측정 화면이다. 근접 알림 값이 있으면 거리 차(앞서면 +, 뒤처지면 −), 없거나 꺼졌으면(100m 밖)
 *   거리 차 칸을 비운다. 일시정지 조작이 없는 러닝이 멈추면 측정 화면에 멈춘 값을 그대로 둔다.
 * - 계획 거리가 있으면 목표 고리, 없으면 1km 구간 고리다.
 * - 워치 안내(길게 눌러 종료, 폰과 연결 필요)가 먼저이고, 없을 때 폰 연결이 끊겼으면 "폰 연결 끊김"이다. 폰이 보낸 문구는 띠 문구다.
 * 심박 표본의 시각은 화면이 쓰지 않아 [Instant.EPOCH]로 둔다(30초 빈칸 규칙은 [WatchViewModel]이 이미 적용했다).
 */
internal fun WatchUiState.toMeasureFlow(): WatchFlow {
    val progress = RunProgress(
        elapsed = Duration.ofSeconds(elapsedSeconds.coerceAtLeast(0)),
        distance = Distance(distanceMeters.coerceAtLeast(0)),
        currentPace = paceSecondsPerKm?.takeIf { it > 0 }?.let(::Pace),
        averagePace = null,
        signal = null,
        heartRate = heartRateBpm?.let { HeartRateSample(Instant.EPOCH, it) },
        isPaused = isPaused
    )
    val goal = planDistanceMeters?.takeIf { it > 0 }?.let(::Distance)
    val flowNotice = when (notice) {
        WatchNotice.HoldToEnd -> WatchFlow.Notice.HOLD_TO_END
        WatchNotice.PhoneNotConnected -> WatchFlow.Notice.PHONE_NOT_CONNECTED
        is WatchNotice.FromPhone, null -> null
    } ?: WatchFlow.Notice.LINK_LOST.takeIf { isLinkLost }
    val banner = (notice as? WatchNotice.FromPhone)?.text
    if (isPaused && isPausable) return WatchFlow.Paused(progress, goal, flowNotice, banner)
    return WatchFlow.Running(
        mode = if (isPausable) WatchMode.FREE else WatchMode.BATTLE,
        progress = progress,
        goal = goal,
        gapMeters = proximity?.let { if (it.isAhead) it.gapMeters else -it.gapMeters },
        banner = banner,
        isLeadChanged = isLeadChanged,
        leadKind = leadKind,
        isPausable = isPausable,
        notice = flowNotice
    )
}
