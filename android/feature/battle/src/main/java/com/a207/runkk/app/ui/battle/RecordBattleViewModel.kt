package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 나와의 싸움·유명인 대결 공통 흐름(FR-096·FR-097·FR-100): 후보 조회 → 고르기 → 누르면 바로 대결 진행 화면의 3초 카운트다운
 * (BattleRunCoordinator.startRecordBattle, 시작 요청은 카운트다운 중에 받는다, S15P31A207-335 9회차) → 측정(design-review.md M34).
 * 진행 화면의 카운트다운(D-2·E-2)이 상대 기록·날짜·내 최고 기록을 그리도록 고른 후보의 값을 경로 인자로 넘긴다. 셀럽 그림 키·상대 모습은
 * 후보 값이고, 시작 응답에 있으면 진행 화면이 응답 값으로 바꾼다.
 * 처음에는 고른 후보가 없다(D-288).
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

    /** 기기에 남긴 후보 사본(시작 로딩이 받아 둔 것, S15P31A207-345 2차). 없으면 null. */
    protected open suspend fun cached(): RecordBattleCandidates? = null

    /** [cached] 가운데 기다리지 않고 바로 줄 수 있는 것(이미 읽어 둔 사본, S15P31A207-345 9회차). 없으면 null. */
    protected open fun peek(): RecordBattleCandidates? = null

    fun load() {
        loadJob?.cancel()
        // 이미 읽어 둔 사본이 있으면 첫 상태부터 그것으로 그려 로딩 원을 한 프레임도 그리지 않는다(9회차).
        val peeked = if (mutableState.value.candidates.isEmpty()) peek() else null
        mutableState.update {
            it.copy(
                isLoading = true,
                isShowingCopy = peeked != null,
                error = null,
                candidates = peeked?.candidates?.map(CandidateUi::of) ?: it.candidates
            )
        }
        loadJob = viewModelScope.launch {
            // 처음 열 때 받아 둔 사본이 있으면 먼저 그려 네트워크 없이 고를 수 있게 하고, 뒤에서 새로 받아 바꾼다.
            val copy = peeked ?: if (mutableState.value.candidates.isEmpty()) cached() else null
            if (copy != null && peeked == null) {
                mutableState.update { it.copy(candidates = copy.candidates.map(CandidateUi::of), isShowingCopy = true) }
            }
            when (val result = fetch()) {
                is Outcome.Success -> mutableState.update { s ->
                    val list = result.value.candidates.map(CandidateUi::of)
                    s.copy(
                        isLoading = false,
                        isShowingCopy = false,
                        candidates = list,
                        selected = s.selected?.takeIf { it in list }
                    )
                }

                // 사본을 그리고 있으면 오류 화면으로 덮지 않고 사본으로 고르게 둔다(대결 생성은 그대로 서버에 묻는다).
                is Outcome.Failure -> mutableState.update {
                    if (copy != null) {
                        it.copy(isLoading = false)
                    } else {
                        it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                    }
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
        // 짝지은 워치가 측정 중이면 서버에 대결을 만들지 않는다(FR-252, D-373): 3초 카운트다운 뒤 측정 시작에서 거부되지 않게.
        if (coordinator.isWatchMeasuring) {
            mutableState.update {
                it.copy(message = BattleErrorText.of(AppError.Rejected(AppError.Rejected.WATCH_MEASURING)))
            }
            return
        }
        mutableState.update { it.copy(isStarting = true) }
        // 서버 응답을 기다리지 않고 카운트다운부터 연다(S15P31A207-335 9회차). 대결 만들기는 카운트다운 중에 조정자가 받고, 실패하면
        // 진행 화면이 멈추고 다시 시도를 보인다. 진행 화면으로 넘어갈 때까지 다시 누르지 못하게 isStarting을 두고 화면에 돌아오면 푼다.
        val ticket = coordinator.startRecordBattle(candidate.distance) { begin(candidate).also(::onBegun) }
        _events.trySend(
            RecordBattleEvent.Start(
                Routes.BattleRunning(
                    ticket,
                    candidate.name,
                    candidate.distance.meters,
                    kind.name,
                    opponentRecordSeconds = candidate.finishTime?.seconds,
                    opponentRecordedOnEpochDay = candidate.recordedOn?.toEpochDay(),
                    myBestSeconds = candidate.myBest?.seconds,
                    celebrityFigure = candidate.celebrityFigure,
                    // 셀프런은 그 기록의 나(후보에 실린 모습, 응답 값이 오면 진행 화면이 바꾼다)다. 셀럽은 null이다.
                    opponentLook = candidate.look?.toSlot()
                )
            )
        )
    }

    /** 대결 만들기 응답(카운트다운 중). 지워진 셀럽 기록이면 안내하고 고른 것을 풀어 목록을 다시 불러온다. */
    private fun onBegun(started: Outcome<RecordBattleStart>) {
        val error = (started as? Outcome.Failure)?.error ?: return
        if ((error as? AppError.Rejected)?.code != CELEBRITY_REMOVED) return
        mutableState.update { it.copy(message = BattleErrorText.of(error), selected = null) }
        load()
    }

    private companion object {
        const val CELEBRITY_REMOVED = "CELEBRITY_REMOVED"
    }
}
