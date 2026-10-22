package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.FreeRunGoal
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

/** 홈의 러닝 종류 탭(자유런 / 배틀런). */
enum class HomeRunMode { FREE, BATTLE }

/** 홈 상단(하이파이 HiHome): 닉네임, 티어·레벨, 리워드 잔액. 못 읽은 값은 null이고 화면은 그 자리를 비워 둔다. */
data class HomeHeader(
    val nickname: String? = null,
    val tier: String? = null,
    val level: Int? = null,
    val balance: Int? = null
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboard: Dashboard? = null,
    /** 자유런 카드의 목표(코치 계획 따르기 켬 + 오늘 계획 → 오늘 계획, 그 밖 → 자유런 설정 목표, FR-048). */
    val freeRunGoal: FreeRunGoal = FreeRunGoal.NONE,
    val pendingCount: Int = 0,
    val mode: HomeRunMode = HomeRunMode.FREE,
    val error: UiText? = null,
    val today: LocalDate? = null,
    val header: HomeHeader = HomeHeader(),
    val isHeaderLoading: Boolean = true,
    /** 우리 집 아바타 자리의 셀카 얼굴 부위 그림. 비어 있으면 "아바타" 자리 그림. */
    val faceLayers: List<FaceLayer> = emptyList()
)

/**
 * 홈(A5, FR-050~FR-057·FR-077): 대시보드(RecordRepository.dashboard), 오늘 계획(todayPlan), 미전송 기록 수
 * (PendingRunRepository.observePending, 13.2)를 모은다. 업무 규칙(스트릭·랜드마크 계산)은 서버가 한다.
 * 자유런 카드의 목표는 FreeRunGoalUseCase.observe로 오늘 계획과 설정 사본을 함께 구독해 고른다: 자유런 설정을 바꾸고
 * 돌아오면 다시 읽지 않아도 바로 바뀐다(FR-048).
 * 상단의 닉네임·티어·레벨은 AccountRepository.profile(SELF), 리워드 잔액은 AvatarRepository.avatar().balance에서 읽는다
 * (하이파이 HiHome 헤더. 5.1.2 표의 ui.home 의존에 domain.account·domain.avatar를 더했다).
 * 오늘 계획을 읽으면 TodayPlanCache에도 알려 워치 시작 화면이 같은 계획을 보이게 한다(WatchHomeSync, FR-042).
 * 읽지 못하면 알리지 않는다(통신 오류로 워치의 계획을 지우지 않게).
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
            val dashboardResult = dashboard.await()
            val planResult = plan.await()
            val me = profile.await()
            val myAvatar = wallet.await()
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
                    faceLayers = face,
                    error = (dashboardResult as? Outcome.Failure)?.let { failure -> RunkkErrorText.of(failure.error) }
                )
            }
        }
    }

    fun onModeSelected(mode: HomeRunMode) = _state.update { it.copy(mode = mode) }
}
