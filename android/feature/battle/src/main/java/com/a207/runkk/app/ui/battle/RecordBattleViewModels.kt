package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 기록 대결 후보 한 줄(나와의 싸움 거리별 최고 기록, 유명인 공개 기록). */
data class CandidateUi(
    val celebrityId: Long?,
    val name: String,
    val distance: Distance,
    val finishTime: Duration?,
    val recordedOn: LocalDate?,
    val averagePace: Pace?,
    val sourceLabel: String?,
    val isEstimated: Boolean
) {
    /** 고를 수 있는가(기록이 없으면 "아직 기록이 없어요", FR-096). */
    val isSelectable: Boolean get() = finishTime != null

    companion object {
        fun of(c: RecordBattleCandidates.Candidate) = CandidateUi(
            celebrityId = c.id,
            name = c.displayName,
            distance = c.distance,
            finishTime = c.finishTime,
            recordedOn = c.recordedOn,
            averagePace = c.finishTime?.takeIf { c.distance.meters > 0 }?.let { Pace.of(it, c.distance) },
            sourceLabel = c.sourceLabel,
            isEstimated = c.isEstimated
        )
    }
}

data class RecordBattleUiState(
    val isLoading: Boolean = true,
    val candidates: List<CandidateUi> = emptyList(),
    /** 유명인 거리 거르기(전체·5K·10K·하프·풀). 나와의 싸움은 쓰지 않는다. */
    val filterIndex: Int = 0,
    /** 고른 후보. 나와의 싸움은 바로 아래 "대결 시작"에, 유명인은 확인 대화상자에 쓴다. */
    val selected: CandidateUi? = null,
    val isConfirming: Boolean = false,
    val isStarting: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)

/** 기록 대결 화면의 한 번 처리 이동. */
sealed interface RecordBattleEvent {
    data class Start(val key: Routes.BattleRunning) : RecordBattleEvent
}

/**
 * 나와의 싸움·유명인 대결 공통 흐름(FR-096·FR-097·FR-100): 후보 조회 → 고르기 → 시작 요청 →
 * BattleRunCoordinator.onRecordBattleStart(3초 카운트다운 뒤 측정, design-review.md M34) → 대결 진행 화면.
 */
abstract class RecordBattleViewModel(
    protected val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val kind: OpponentKind
) : ViewModel() {
    protected val mutableState = MutableStateFlow(RecordBattleUiState())
    val state: StateFlow<RecordBattleUiState> = mutableState.asStateFlow()

    private val _events = Channel<RecordBattleEvent>(Channel.BUFFERED)
    val events: Flow<RecordBattleEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    protected abstract suspend fun fetch(): Outcome<RecordBattleCandidates>

    protected abstract suspend fun begin(candidate: CandidateUi): Outcome<RecordBattleStart>

    fun load() {
        loadJob?.cancel()
        mutableState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = fetch()) {
                is Outcome.Success -> mutableState.update { s ->
                    val list = result.value.candidates.map(CandidateUi::of)
                    s.copy(isLoading = false, candidates = list, selected = s.selected?.takeIf { it in list })
                }

                is Outcome.Failure -> mutableState.update {
                    it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }

    fun onSelect(candidate: CandidateUi) {
        if (!candidate.isSelectable) return
        mutableState.update { it.copy(selected = candidate) }
    }

    fun onMessageShown() = mutableState.update { it.copy(message = null) }

    /** 화면에 다시 돌아왔을 때(대결을 마치고 탭으로 복귀). */
    fun onScreenResumed() = mutableState.update { it.copy(isStarting = false) }

    /** 고른 후보로 대결을 시작한다. 이미 시작 중이면 무시한다. */
    fun onStart() {
        val s = mutableState.value
        val candidate = s.selected ?: return
        if (s.isStarting) return
        mutableState.update { it.copy(isStarting = true, isConfirming = false) }
        viewModelScope.launch {
            when (val started = begin(candidate)) {
                is Outcome.Success -> {
                    val start = started.value
                    coordinator.onRecordBattleStart(start)
                    // 진행 화면으로 넘어갈 때까지 다시 누르지 못하게 isStarting을 둔다. 화면에 돌아오면 onScreenResumed가 푼다.
                    _events.trySend(
                        RecordBattleEvent.Start(
                            Routes.BattleRunning(start.battleId, start.opponentName, start.distance.meters, kind.name)
                        )
                    )
                }

                is Outcome.Failure -> {
                    mutableState.update {
                        it.copy(isStarting = false, message = BattleErrorText.of(started.error))
                    }
                    if ((started.error as? AppError.Rejected)?.code == CELEBRITY_REMOVED) {
                        mutableState.update { it.copy(selected = null) }
                        load()
                    }
                }
            }
        }
    }

    private companion object {
        const val CELEBRITY_REMOVED = "CELEBRITY_REMOVED"
    }
}

/** 셀프 탭: 나와의 싸움(거리별 본인 최고 기록, FR-096). */
@HiltViewModel
class SelfBattleViewModel @Inject constructor(battles: BattleRepository, coordinator: BattleRunCoordinator) :
    RecordBattleViewModel(battles, coordinator, OpponentKind.SELF) {
    init {
        load()
    }

    override suspend fun fetch() = battles.selfCandidates()

    override suspend fun begin(candidate: CandidateUi) = battles.startSelfBattle(candidate.distance)
}

/** 셀럽 탭: 유명인 공개 기록 대결(FR-097·FR-100). 거리로 거르고, 고르면 확인을 받은 뒤 시작한다. */
@HiltViewModel
class CelebBattleViewModel @Inject constructor(battles: BattleRepository, coordinator: BattleRunCoordinator) :
    RecordBattleViewModel(battles, coordinator, OpponentKind.CELEBRITY) {
    init {
        load()
    }

    override suspend fun fetch() = battles.celebrityCandidates(CELEB_FILTERS[mutableState.value.filterIndex].second)

    override suspend fun begin(candidate: CandidateUi): Outcome<RecordBattleStart> {
        val id = candidate.celebrityId ?: return Outcome.Failure(AppError.Rejected("CELEBRITY_REMOVED"))
        return battles.startCelebrityBattle(id)
    }

    fun onFilter(index: Int) {
        if (index == mutableState.value.filterIndex || index !in CELEB_FILTERS.indices) return
        mutableState.update { it.copy(filterIndex = index, selected = null) }
        load()
    }

    /** 유명인 기록을 고르면 확인 대화상자를 연다. */
    fun onPick(candidate: CandidateUi) {
        if (!candidate.isSelectable) return
        mutableState.update { it.copy(selected = candidate, isConfirming = true) }
    }

    fun onDismissConfirm() = mutableState.update { it.copy(isConfirming = false) }
}

/** 유명인 거리 거르기(FR-097): 라벨 → 거리(전체는 null). */
val CELEB_FILTERS: List<Pair<UiText, Distance?>> = listOf(
    UiText.Res(R.string.battle_filter_all) to null,
    UiText.Plain("5K") to Distance(5_000),
    UiText.Plain("10K") to Distance(10_000),
    UiText.Res(R.string.battle_distance_half) to Distance(HALF_MARATHON_METERS),
    UiText.Res(R.string.battle_distance_full) to Distance(FULL_MARATHON_METERS)
)
