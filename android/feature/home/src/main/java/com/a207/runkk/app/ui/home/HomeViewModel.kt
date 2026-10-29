package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 홈(A-4, D-276, FR-050~FR-053): 대시보드(RecordRepository.dashboard)의 이번 주 거리·목표, 연속 일수, 리워드 잔액과
 * 본인 프로필(닉네임·티어·레벨, 얼굴·배경 키), 러닝 설정의 음성·진동을 모은다. 리워드 잔액은 대시보드 rewardBalance라
 * domain.avatar에 기대지 않는다(D-276).
 * 오늘 계획을 읽으면 TodayPlanCache에도 알려 워치 시작 화면이 같은 계획을 보이게 한다(WatchHomeSync, FR-042).
 * 자유런 카드는 위치 수집 동의를 먼저 본다(D-267): 동의가 없으면 시트를 띄우고, "동의하고 시작"이면 동의를 준 뒤 시작한다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val records: RecordRepository,
    private val account: AccountRepository,
    private val locationConsent: LocationConsentUseCase,
    private val todayPlans: TodayPlanCache,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private var refreshJob: Job? = null

    private var resumedOnce = false

    private var consentJob: Job? = null

    init {
        refresh()
    }

    /** 홈이 다시 보일 때(러닝 결과의 "홈으로", 다른 탭에서 복귀) 다시 읽는다. 처음 표시는 init이 읽으므로 첫 재개는 건너뛴다. */
    fun onResumed() {
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    fun refresh() {
        val today = LocalDate.now(clock)
        _state.update { it.copy(isLoading = true, error = null) }
        // 늦게 끝난 앞 요청이 새 결과를 덮지 않게 취소한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val dashboard = async { records.dashboard(today) }
            val plan = async { records.todayPlan() }
            val profile = async { account.profile(AccountRepository.SELF).getOrNull() }
            val settings = async { account.settings().getOrNull() }
            val dashboardResult = dashboard.await()
            val planResult = plan.await()
            if (planResult is Outcome.Success) {
                todayPlans.put(planResult.value?.let { PlanSnapshot(it.distance, it.pace) })
            }
            val me = profile.await()
            val memberSettings = settings.await()
            val face = me?.faceKey?.let { account.faceLayers(it).getOrNull() }.orEmpty()
            val summary = dashboardResult.getOrNull()
            _state.update {
                it.withSummary(summary).copy(
                    isLoading = false,
                    // 프로필을 못 읽었으면 지난 값을 남긴다(대시보드와 같은 규칙).
                    header = me?.let { p -> HomeHeader(p.displayName, p.tier, p.level, summary?.rewardBalance) }
                        ?: it.header.copy(balance = summary?.rewardBalance ?: it.header.balance),
                    isHeaderLoading = false,
                    faceKey = if (me != null) me.faceKey else it.faceKey,
                    faceLayers = if (me != null) face else it.faceLayers,
                    backgroundKey = if (me != null) me.avatarBackgroundKey else it.backgroundKey,
                    isRunVoiceOn = memberSettings?.values?.get(RUN_VOICE)?.toBooleanStrictOrNull() ?: it.isRunVoiceOn,
                    isRunVibrationOn =
                        memberSettings?.values?.get(RUN_VIBRATION)?.toBooleanStrictOrNull() ?: it.isRunVibrationOn,
                    error = (dashboardResult as? Outcome.Failure)?.let { failure -> RunkkErrorText.of(failure.error) }
                )
            }
        }
    }

    /** 자유런 카드: 위치 수집 동의가 있으면 바로 시작, 없으면 동의 시트(D-267). */
    fun onFreeRunTapped() {
        if (locationConsent.isGranted()) {
            _events.trySend(HomeEvent.StartRun)
        } else {
            _state.update { it.copy(isConsentSheetVisible = true) }
        }
    }

    /** 시트 "동의하고 시작": 동의를 주고 사본을 갱신한 뒤 시작한다. 실패하면 시작하지 않고 알린다. 연타는 한 번만 보낸다. */
    fun onConsentAgreed() {
        if (consentJob?.isActive == true) return
        _state.update { it.copy(isConsentSheetVisible = false) }
        consentJob = viewModelScope.launch {
            when (val given = locationConsent.give()) {
                is Outcome.Success -> _events.send(HomeEvent.StartRun)
                is Outcome.Failure -> _state.update { it.copy(message = RunkkErrorText.of(given.error)) }
            }
        }
    }

    fun onConsentClosed() = _state.update { it.copy(isConsentSheetVisible = false) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private companion object {
        /** 러닝 설정 하위 항목 키(MemberSettings.values, 서버 RunSettings.voice·vibration). */
        const val RUN_VOICE = "RUN_SETTINGS.voice"
        const val RUN_VIBRATION = "RUN_SETTINGS.vibration"
    }
}

/** 대시보드에서 홈이 그리는 값만 옮긴다. 읽지 못했으면 지난 값을 그대로 둔다. 목표 0m는 목표 없음이다. */
private fun HomeUiState.withSummary(summary: Dashboard?): HomeUiState = if (summary == null) {
    this
} else {
    copy(
        weeklyDistance = summary.weekly?.actualDistance ?: Distance.ZERO,
        weeklyGoal = summary.weekly?.goalDistance?.takeIf { it.meters > 0 },
        streakDays = summary.streakDays
    )
}
