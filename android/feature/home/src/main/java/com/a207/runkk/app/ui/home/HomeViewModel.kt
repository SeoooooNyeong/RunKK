package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PendingRunRepository
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
 * 본인 요약(MySummary: 닉네임·티어·레벨, 얼굴·배경 키, D-331), 러닝 설정의 음성·진동을 모은다. 리워드 잔액은 대시보드 rewardBalance라
 * domain.avatar에 기대지 않는다(D-276).
 * 오늘 계획을 읽으면 TodayPlanCache에도 알려 워치 시작 화면이 같은 계획을 보이게 한다(WatchHomeSync, FR-042).
 * 자유런 카드는 위치 수집 동의를 먼저 본다(D-267): 동의가 없으면 시트를 띄우고, "동의하고 시작"이면 동의를 준 뒤 시작한다.
 * 미전송 기록이 재전송 안내 기준을 넘으면 상단에 안내 띠를 보인다(FR-039, W48). 보관함이 바뀔 때와 홈이 다시 보일 때 지금 시각으로
 * 다시 세고, 조건이 풀리면 띠를 내린다. 안내만 하고 기록을 지우거나 측정을 막지 않는다(W47).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val records: RecordRepository,
    private val account: AccountRepository,
    private val locationConsent: LocationConsentUseCase,
    private val todayPlans: TodayPlanCache,
    private val pendingRuns: PendingRunRepository,
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
        viewModelScope.launch { pendingRuns.observePending().collect { checkResendReminder() } }
        refresh()
    }

    /**
     * 홈이 다시 보일 때(러닝 결과의 "홈으로", 다른 탭에서 복귀) 다시 읽는다. 처음 표시는 init이 읽으므로 첫 재개는 건너뛴다.
     * 「내 기록 요약」은 홈에 들어올 때마다 접힌다(J24-16).
     */
    fun onResumed() {
        _state.update { it.copy(isSummaryExpanded = false) }
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    /** 「내 기록 요약 ⌄」·「접기 ⌃」. */
    fun onSummaryToggle() = _state.update { it.copy(isSummaryExpanded = !it.isSummaryExpanded) }

    /** 최근 러닝 한 줄: 그 기록 상세로 간다. */
    fun onLatestRunTapped(recordId: String) {
        _events.trySend(HomeEvent.Navigate(Routes.RecordDetail(recordId)))
    }

    /** 배틀런 카드: 진입 시트를 열고 배치 진행을 한 번 다시 읽는다(배치고사를 마친 직후에도 맞는 카드를 보이게, D-499). */
    fun onBattleCardTapped() {
        _state.update { it.copy(isBattleEntryVisible = true) }
        viewModelScope.launch { applyPlacement(account.placement()) }
    }

    fun onBattleEntryDismissed() = _state.update { it.copy(isBattleEntryVisible = false) }

    /** 랜덤 카드: 배치 중이거나 진행을 모르면 배치고사 화면(그 화면이 다시 읽는다), 확정 티어면 랜덤 대결(D-499). */
    fun onRandomChosen() {
        val placement = _state.value.placement
        _state.update { it.copy(isBattleEntryVisible = false) }
        val route = if (placement == null || placement.status == PlacementStatus.IN_PROGRESS) {
            Routes.PLACEMENT
        } else {
            Routes.BATTLE_RANDOM
        }
        _events.trySend(HomeEvent.Navigate(Routes.Key(route)))
    }

    /** 친구 카드: 친구 대결 화면. */
    fun onFriendChosen() {
        _state.update { it.copy(isBattleEntryVisible = false) }
        _events.trySend(HomeEvent.Navigate(Routes.Key(Routes.BATTLE_FRIEND)))
    }

    /** 배치 진행을 읽었으면 진행과 헤더 배지(본인 표시, D-494)를 바꾸고, 못 읽었으면 지난 값을 둔다. */
    private fun applyPlacement(result: Outcome<PlacementProgress>) {
        val progress = (result as? Outcome.Success)?.value ?: return
        _state.update {
            it.copy(placement = progress, tierDisplay = TierDisplay.ofSelf(progress.tier, progress.provisionalTier))
        }
    }

    fun refresh() {
        val today = LocalDate.now(clock)
        _state.update { it.copy(isLoading = true, error = null) }
        // 늦게 끝난 앞 요청이 새 결과를 덮지 않게 취소한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            checkResendReminder()
            val dashboard = async { records.dashboard(today) }
            val plan = async { records.todayPlan() }
            val profile = async { account.mySummary().getOrNull() }
            val settings = async { account.settings().getOrNull() }
            val placement = async { account.placement() }
            val dashboardResult = dashboard.await()
            val planResult = plan.await()
            if (planResult is Outcome.Success) {
                todayPlans.put(planResult.value?.let { PlanSnapshot(it.distance, it.pace) })
            }
            val me = profile.await()
            val memberSettings = settings.await()
            val face = me?.faceKey?.let { account.faceLayers(it).getOrNull() }.orEmpty()
            val summary = dashboardResult.getOrNull()
            applyPlacement(placement.await())
            _state.update {
                // 대시보드·프로필을 못 읽었으면 그 값은 지난 값을 남긴다(AN8-11). 읽었는데 잔액이 없으면(옛 서버) 비운다.
                val balance = if (dashboardResult is Outcome.Success) summary?.rewardBalance else it.header.balance
                it.withSummary(summary, today).copy(
                    isLoading = false,
                    header = me?.let { p -> HomeHeader(p.displayName, p.tier, p.level, balance) }
                        ?: it.header.copy(balance = balance),
                    isHeaderLoading = false,
                    isAvatarReady = it.isAvatarReady || me != null,
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

    /** 재전송 안내 띠(W48). 보관함을 읽지 못하면 띠를 보이지 않는다. */
    private suspend fun checkResendReminder() {
        val isNeeded = pendingRuns.needsReminder(clock.instant()).getOrNull() == true
        _state.update { it.copy(isResendReminderVisible = isNeeded) }
    }

    private companion object {
        /** 러닝 설정 하위 항목 키(MemberSettings.values, 서버 RunSettings.voice·vibration). */
        const val RUN_VOICE = "RUN_SETTINGS.voice"
        const val RUN_VIBRATION = "RUN_SETTINGS.vibration"
    }
}

/** 대시보드에서 홈이 그리는 값만 옮긴다. 읽지 못했으면 지난 값을 그대로 둔다. 목표 0m는 목표 없음이다. */
private fun HomeUiState.withSummary(summary: Dashboard?, today: LocalDate): HomeUiState = if (summary == null) {
    this
} else {
    copy(
        weeklyDistance = summary.weekly?.actualDistance ?: Distance.ZERO,
        weeklyGoal = summary.weekly?.goalDistance?.takeIf { it.meters > 0 },
        streakWeeks = summary.streakWeeks,
        summary = HomeSummary.of(summary, today)
    )
}
