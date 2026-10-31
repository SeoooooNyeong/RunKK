package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import com.a207.runkk.app.domain.run.WatchHomeSync
import com.a207.runkk.app.domain.settings.FirstRunGuide
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 기기 사본을 서버와 맞추는 조정자(D-332, design-review 19.5.3 R52). app의 AppStartup이 앱 시작과 로그인 상태 전환 때 부른다.
 * 맞추는 것은 보관 기록의 파기 지시, 설정 사본, 푸시 토큰, 오늘 계획 사본, 첫 사용 가이드 끝냄이다. 목적이 하나(기기 사본을 서버와
 * 맞추기)라 통신적 응집이다. 시작 때 한다는 이유만으로 다른 일(로그 초기화 등)을 여기에 넣지 않는다(시간적 응집 방지, 5.1.2).
 *
 * 로그인 여부를 매개변수로 받지 않고 연산을 나눈다(제어 결합 방지): 로그인과 무관한 사본은 [syncDevice], 회원 사본은
 * [syncAccount], 로그아웃 정리는 [clearAccount]다. 어느 것을 부를지는 로그인 상태를 보는 app이 정한다.
 * 각 연산은 실패해도 멈추지 않고 다음 사본으로 넘어가며, 실패한 사본과 오류를 순서대로 돌려준다(app이 기록만 하고 다음 시작·다음
 * 로그인 때 다시 한다).
 */
class DeviceCopySync
@Inject
constructor(
    private val syncDisposals: SyncDisposalsUseCase,
    private val settings: SettingsRepository,
    private val pushToken: PushTokenSync,
    private val records: RecordRepository,
    private val todayPlans: TodayPlanCache,
    private val watchHome: WatchHomeSync,
    private val firstRunGuide: FirstRunGuide
) {
    /** 워치 시작 화면 맞추기를 시작한다(WatchHomeSync, FR-042). 앱 시작 때 한 번 부른다. */
    fun start() = watchHome.start()

    /**
     * 로그인과 무관한 사본: 파기 지시 적용 → 설정 사본 갱신 → 푸시 토큰 등록. 푸시 토큰은 로그아웃 상태면 PushTokenSync가
     * 건너뛰고, 실패는 그쪽이 다음 기회에 다시 보낸다.
     */
    suspend fun syncDevice(): Map<DeviceCopy, AppError> {
        val failures = linkedMapOf<DeviceCopy, AppError>()
        failures.note(DeviceCopy.DISPOSALS, syncDisposals())
        failures.note(DeviceCopy.SETTINGS, settings.refresh())
        pushToken.syncCurrent()
        return failures
    }

    /**
     * 로그인한 회원의 사본: 오늘 계획을 읽어 사본에 넣고(못 읽으면 아는 계획을 그대로 둔다) 서버에 알리지 못한 가이드 끝냄을
     * 다시 보낸다.
     */
    suspend fun syncAccount(): Map<DeviceCopy, AppError> {
        val failures = linkedMapOf<DeviceCopy, AppError>()
        when (val plan = records.todayPlan()) {
            is Outcome.Success -> todayPlans.put(plan.value?.let { PlanSnapshot(it.distance, it.pace) })
            is Outcome.Failure -> failures[DeviceCopy.TODAY_PLAN] = plan.error
        }
        failures.note(DeviceCopy.GUIDE_COMPLETION, firstRunGuide.resendCompletion())
        return failures
    }

    /** 로그아웃 정리: 설정 사본을 지우고 오늘 계획 사본을 비운다(이전 회원의 계획이 워치 시작 화면에 남지 않게). */
    suspend fun clearAccount(): Map<DeviceCopy, AppError> {
        val failures = linkedMapOf<DeviceCopy, AppError>()
        failures.note(DeviceCopy.SETTINGS, settings.clear())
        todayPlans.put(null)
        return failures
    }
}

private fun MutableMap<DeviceCopy, AppError>.note(copy: DeviceCopy, result: Outcome<*>) {
    if (result is Outcome.Failure) this[copy] = result.error
}
