package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 자유런 목표를 설정 사본(SettingsRepository, D-53)으로 고른다. ui.run(시작 조건)과 ui.home(자유런 카드)이
 * domain.settings를 직접 보지 않고 같은 규칙(FreeRunGoal.of)을 쓰게 하는 창구다.
 */
class FreeRunGoalUseCase @Inject constructor(private val settings: SettingsRepository) {
    /** 지금 설정 사본으로 고른 목표(카운트다운을 시작할 때 읽는다). */
    fun current(todayPlan: PlanSnapshot?): FreeRunGoal = FreeRunGoal.of(todayPlan, settings.guidanceSettings())

    /** 오늘 계획이나 설정 사본이 바뀔 때마다 목표를 다시 낸다(설정을 바꾸고 홈에 돌아오면 바로 반영). */
    fun observe(todayPlan: Flow<PlanSnapshot?>): Flow<FreeRunGoal> =
        combine(todayPlan, settings.changes()) { plan, snapshot -> FreeRunGoal.of(plan, snapshot.guidanceSettings) }
            .distinctUntilChanged()
}
