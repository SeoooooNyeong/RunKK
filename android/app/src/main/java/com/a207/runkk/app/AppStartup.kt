package com.a207.runkk.app

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.LoginState
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import com.a207.runkk.app.domain.run.WatchHomeSync
import com.a207.runkk.app.domain.settings.FirstRunGuide
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.sync.SyncDisposalsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 앱 시작 작업과 로그인 상태 전환 작업(6.1.14 RunkkApp, 13.2, D-43·D-53·D-54). RunkkApp.onCreate가 start()를 한 번 부른다.
 * 모든 작업은 applicationScope 위에서 차례로 하고 실패는 기록만 한다(다음 시작·다음 로그인 때 다시 한다).
 *
 * - 시작: 워치 시작 화면 맞추기 시작(WatchHomeSync.start, FR-042) → 파기 지시 적용(SyncDisposalsUseCase)
 *   → 설정 사본 갱신(SettingsRepository.refresh) → 푸시 토큰 등록(PushTokenSync.syncCurrent)
 *   → 로그인 상태면 오늘 계획 읽기(RecordRepository.todayPlan → TodayPlanCache)와 서버에 알리지 못한 첫 사용 가이드 끝냄 다시 보내기
 *   (FirstRunGuide.resendCompletion, D-277)
 * - 로그인 상태가 참이 되면: 같은 작업을 다시 한다(로그아웃 상태로 시작했거나 세션이 만료됐다가 다시 로그인한 경우)
 * - 로그인 상태가 거짓이 되면: 설정 사본을 지우고(SettingsRepository.clear) 오늘 계획 사본도 비운다(이전 회원의 계획이
 *   워치 시작 화면에 남지 않게)
 * 워치 시작 화면은 WatchHomeSync가 오늘 계획·설정 사본이 바뀔 때마다 다시 보낸다.
 */
@Singleton
class AppStartup
@Inject
constructor(
    private val syncDisposals: SyncDisposalsUseCase,
    private val settings: SettingsRepository,
    private val pushToken: PushTokenSync,
    private val loginState: LoginState,
    private val records: RecordRepository,
    private val todayPlans: TodayPlanCache,
    private val watchHome: WatchHomeSync,
    private val firstRunGuide: FirstRunGuide,
    private val scope: CoroutineScope
) {
    fun start() {
        watchHome.start()
        scope.launch {
            // 시작 때 값을 먼저 잡아 두고, 그 뒤 값이 바뀔 때만 전환 작업을 한다(시작 작업과 겹쳐 두 번 하지 않는다).
            var previous = loginState.loggedIn.value
            syncAll()
            loginState.loggedIn.collect { loggedIn ->
                if (loggedIn == previous) return@collect
                previous = loggedIn
                if (loggedIn) {
                    syncAll()
                } else {
                    report("설정 사본 삭제", settings.clear())
                    todayPlans.put(null)
                }
            }
        }
    }

    private suspend fun syncAll() {
        report("파기 지시 적용", syncDisposals())
        report("설정 사본 갱신", settings.refresh())
        pushToken.syncCurrent()
        if (loginState.loggedIn.value) {
            refreshTodayPlan()
            report("가이드 끝냄 다시 보내기", firstRunGuide.resendCompletion())
        }
    }

    /** 오늘 계획을 읽어 사본에 둔다. 읽지 못하면 아는 계획을 그대로 둔다(홈을 열 때 다시 읽는다). */
    private suspend fun refreshTodayPlan() {
        when (val plan = records.todayPlan()) {
            is Outcome.Success -> todayPlans.put(plan.value?.let { PlanSnapshot(it.distance, it.pace) })
            is Outcome.Failure -> report("오늘 계획 읽기", plan)
        }
    }

    private fun report(task: String, result: Outcome<*>) {
        if (result is Outcome.Failure) Timber.w("시작 작업 실패: %s (%s)", task, result.error)
    }
}
