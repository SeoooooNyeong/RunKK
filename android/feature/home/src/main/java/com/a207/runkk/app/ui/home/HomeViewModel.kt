package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 홈(A5, FR-050~FR-057·FR-077): 대시보드(RecordRepository.dashboard), 오늘 계획(todayPlan), 미전송 기록 수
 * (PendingRunRepository.observePending, 13.2)를 모은다. 업무 규칙(스트릭·랜드마크 계산)은 서버가 한다.
 * 자유런 카드의 목표는 FreeRunGoalUseCase.observe로 오늘 계획과 설정 사본을 함께 구독해 고른다: 자유런 설정을 바꾸고
 * 돌아오면 다시 읽지 않아도 바로 바뀐다(FR-048).
 * 상단의 닉네임·티어·레벨은 AccountRepository.profile(SELF), 리워드 잔액은 AvatarRepository.avatar().balance에서 읽는다
 * (하이파이 HiHome 헤더. 5.1.2 표의 ui.home 의존에 domain.account·domain.avatar를 더했다).
 * 오늘 계획을 읽으면 TodayPlanCache에도 알려 워치 시작 화면이 같은 계획을 보이게 한다(WatchHomeSync, FR-042).
 * 읽지 못하면 알리지 않는다(통신 오류로 워치의 계획을 지우지 않게).
 * 우리 집 배경은 Avatar.backgroundKey(FR-202), 자유런 칩의 음성·진동은 AccountRepository.settings()의 러닝 설정 값이다.
 * 배틀런 탭의 대결 거리는 AccountRepository.settings()의 기본 대결 거리(FR-109)로 처음 고르고, 러너가 칩으로 바꾼다. 고르기만으로는
 * 설정을 바꾸지 않는다: 기본 대결 거리는 매칭을 요청할 때 서버가 그 거리로 저장한다(FR-080, MatchingService.requestMatch).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val records: RecordRepository,
    private val pendingRuns: PendingRunRepository,
    private val account: AccountRepository,
    private val avatar: AvatarRepository,
    private val goals: FreeRunGoalUseCase,
    private val todayPlans: TodayPlanCache,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 오늘 계획(서버 원본). 자유런 목표는 이것과 설정 사본으로 고른다. */
    private val todayPlan = MutableStateFlow<PlanSnapshot?>(null)

    /** 러너가 고른 거리는 늦게 온 기본 대결 거리로 덮지 않는다. */
    private var isBattleDistanceChosen = false

    init {
        viewModelScope.launch {
            goals.observe(todayPlan).collect { goal -> _state.update { it.copy(freeRunGoal = goal) } }
        }
        refresh()
        viewModelScope.launch {
            pendingRuns.observePending().collect { runs -> _state.update { it.copy(pendingCount = runs.size) } }
        }
    }

    private var refreshJob: Job? = null

    private var resumedOnce = false

    /**
     * 홈이 다시 보일 때(러닝 결과의 "홈으로", 다른 탭에서 복귀) 요약을 다시 읽는다. 오늘 달린 거리·누적 거리·랜드마크가 방금 저장한 기록을 따라간다.
     * 처음 표시는 init이 읽으므로 첫 재개는 건너뛴다(두 번 부르지 않는다).
     */
    fun onResumed() {
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    fun refresh() {
        val today = LocalDate.now(clock)
        _state.update { it.copy(isLoading = true, error = null, today = today) }
        // 늦게 끝난 앞 요청이 새 결과를 덮지 않게 취소한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val dashboard = async { records.dashboard(today) }
            val plan = async { records.todayPlan() }
            val profile = async { account.profile(AccountRepository.SELF).getOrNull() }
            val wallet = async { avatar.avatar().getOrNull() }
            val settings = async { account.settings().getOrNull() }
            val dashboardResult = dashboard.await()
            val planResult = plan.await()
            val me = profile.await()
            val myAvatar = wallet.await()
            val memberSettings = settings.await()
            val battleDefault = memberSettings?.battleDefaultDistance?.takeIf { it in HomeUiState.BATTLE_DISTANCES }
            val face = myAvatar?.faceKey?.let { avatar.faceLayers(it).getOrNull() }.orEmpty()
            todayPlan.value = planResult.getOrNull()?.let { PlanSnapshot(it.distance, it.pace) }
            if (planResult is Outcome.Success) todayPlans.put(todayPlan.value)
            val header =
                HomeHeader(
                    nickname = me?.displayName,
                    tier = me?.tier,
                    level = me?.level,
                    balance = myAvatar?.balance
                )
            _state.update {
                it.copy(
                    isLoading = false,
                    dashboard = dashboardResult.getOrNull() ?: it.dashboard,
                    header = header,
                    isHeaderLoading = false,
                    // 얼굴을 고르기 전이면 기본 캐릭터 얼굴 1번(FR-155·FR-158).
                    faceKey = myAvatar?.let {
                        it.faceKey ?: it.defaultCharacterId?.let(RunkkAvatarArt::defaultKey)
                            ?: RunkkAvatarArt.DEFAULT_FACE_KEY
                    },
                    faceLayers = face,
                    backgroundKey = myAvatar?.backgroundKey,
                    isRunVoiceOn = memberSettings?.values?.get(RUN_VOICE)?.toBooleanStrictOrNull() ?: it.isRunVoiceOn,
                    isRunVibrationOn =
                        memberSettings?.values?.get(RUN_VIBRATION)?.toBooleanStrictOrNull() ?: it.isRunVibrationOn,
                    battleDistance = battleDefault?.takeUnless { isBattleDistanceChosen } ?: it.battleDistance,
                    error = (dashboardResult as? Outcome.Failure)?.let { failure -> RunkkErrorText.of(failure.error) }
                )
            }
        }
    }

    fun onModeSelected(mode: HomeRunMode) = _state.update { it.copy(mode = mode) }

    /** 배틀런 대결 거리 고르기(FR-080). 랜덤 대결 거리(1·3·5·10km)가 아니면 무시한다. */
    fun onBattleDistanceSelected(distance: Distance) {
        if (distance !in HomeUiState.BATTLE_DISTANCES) return
        isBattleDistanceChosen = true
        _state.update { it.copy(battleDistance = distance) }
    }

    private companion object {
        /** 러닝 설정 하위 항목 키(MemberSettings.values, 서버 RunSettings.voice·vibration). */
        const val RUN_VOICE = "RUN_SETTINGS.voice"
        const val RUN_VIBRATION = "RUN_SETTINGS.vibration"
    }
}
