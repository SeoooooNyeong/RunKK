package com.a207.runkk.app.domain.run

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 오늘 계획(서버 원본, GET /plans/today)의 앱 메모리 사본(FR-042, FR-048). 기기에 저장하지 않는다.
 * 오늘 계획을 읽거나 바꾼 쪽(app 시작 작업, ui.home, ui.record 코치)이 put으로 알리고, WatchHomeSync가 구독해 워치 시작
 * 화면에 싣는다. 로그아웃하면 app 시작 작업이 비운다(이전 회원의 계획이 남지 않게).
 */
@Singleton
class TodayPlanCache @Inject constructor() {
    private val _plan = MutableStateFlow<PlanSnapshot?>(null)

    /** 마지막으로 알려진 오늘 계획. 없거나 아직 모르면 null. */
    val plan: StateFlow<PlanSnapshot?> = _plan.asStateFlow()

    fun put(plan: PlanSnapshot?) {
        _plan.value = plan
    }
}
