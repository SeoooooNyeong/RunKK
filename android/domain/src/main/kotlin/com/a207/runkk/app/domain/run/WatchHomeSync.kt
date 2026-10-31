package com.a207.runkk.app.domain.run

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 워치 시작 화면(러닝 아님 + 오늘 계획, FR-042·N-21)을 앱이 떠 있는 동안 맞춰 둔다. app 시작 작업(AppStartup)이
 * DeviceCopySync.start로 한 번 부른다(D-332). 오늘 계획(TodayPlanCache)이나 설정 사본이 바뀌어 목표가 바뀔 때마다 RunTracker.publishWatchHome을 다시 부른다.
 *
 * 시작 화면에 싣는 계획은 워치에서 시작을 누르면 실제로 달릴 자유런 목표다(FreeRunGoalUseCase: 코치 계획 따르기 켬 + 오늘
 * 계획 → 오늘 계획, 그 밖 → 자유런 설정 목표 거리, FR-048). 측정 중이면 RunTracker가 계획만 기억했다가 러닝이 끝난 뒤
 * 시작 화면에 보인다.
 */
@Singleton
class WatchHomeSync
@Inject
constructor(
    private val tracker: RunTracker,
    private val goals: FreeRunGoalUseCase,
    private val todayPlan: TodayPlanCache,
    private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    /** 구독을 시작한다(지금 목표로 한 번 보내고, 목표가 바뀔 때마다 다시 보낸다). 두 번째부터는 아무것도 하지 않는다. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // 목표에는 음성·진동 설정도 있어(카운트다운 요약용) 계획이 같으면 다시 보내지 않게 계획만 거른다.
        scope.launch {
            goals.observe(todayPlan.plan).map { it.plan }.distinctUntilChanged().collect(tracker::publishWatchHome)
        }
    }
}
