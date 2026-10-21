package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 판정(서버 battle.Verdict). */
enum class Verdict(@StringRes val labelRes: Int, val headline: String) {
    WIN(R.string.battle_verdict_win, "VICTORY"),
    LOSE(R.string.battle_verdict_lose, "DEFEAT"),
    DRAW(R.string.battle_verdict_draw, "DRAW");

    companion object {
        fun of(value: String?): Verdict? = entries.firstOrNull { it.name == value }
    }
}

data class BattleResultUiState(
    val isLoading: Boolean = true,
    val battleId: Long? = null,
    val result: BattleResult? = null,
    val error: UiText? = null
) {
    val verdict: Verdict? get() = Verdict.of(result?.verdict)

    /** 이번 주 전적(결과 응답 weekly). */
    val weekly: BattleResult.Weekly? get() = result?.weekly
}

/** 판정 문구(시안 HiBattleResult "[상대]님보다 42초 먼저 도착했어요!"). 상대·도달 시간을 모르면 일반 문구다. */
internal fun verdictMessage(verdict: Verdict?, result: BattleResult?): UiText {
    val name = result?.opponentName?.takeIf { it.isNotBlank() }
    val mine = result?.myReach
    val theirs = result?.opponentReach
    val gap = if (mine != null && theirs != null) gapText(theirs.minus(mine).abs()) else null
    return when (verdict) {
        Verdict.WIN -> when {
            name != null && gap != null -> uiText(R.string.battle_result_win_name_gap, name, gap)
            name != null -> uiText(R.string.battle_result_win_name, name)
            else -> uiText(R.string.battle_result_win)
        }

        Verdict.LOSE -> when {
            name != null && gap != null -> uiText(R.string.battle_result_lose_name_gap, name, gap)
            name != null -> uiText(R.string.battle_result_lose_name, name)
            else -> uiText(R.string.battle_result_lose)
        }

        Verdict.DRAW ->
            if (name != null) uiText(R.string.battle_result_draw_name, name) else uiText(R.string.battle_result_draw)

        null -> uiText(R.string.battle_result_checking)
    }
}

/** 시간 차 문구: 42초, 1분 5초. */
internal fun gapText(gap: Duration): UiText {
    val seconds = gap.seconds
    return if (seconds < SECONDS_PER_MINUTE) {
        uiText(R.string.battle_gap_seconds, seconds)
    } else {
        val minutes = seconds / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        if (rest ==
            0L
        ) {
            uiText(R.string.battle_gap_minutes, minutes)
        } else {
            uiText(R.string.battle_gap_minutes_seconds, minutes, rest)
        }
    }
}

/** 이력의 기록 차이(본인 − 상대): 음수면 "42초 빠름", 양수면 "12초 느림", 0이면 "같은 기록". */
internal fun gapLabel(gap: Duration): UiText = when {
    gap.isNegative -> uiText(R.string.battle_gap_faster, gapText(gap.abs()))
    gap.isZero -> uiText(R.string.battle_gap_same)
    else -> uiText(R.string.battle_gap_slower, gapText(gap))
}

/** 이번 주 전적 표기: 3W 1L(무승부가 있으면 1D를 더한다). */
internal fun weeklyLabel(weekly: BattleResult.Weekly): String =
    "${weekly.wins}W ${weekly.losses}L" + if (weekly.draws > 0) " ${weekly.draws}D" else ""

/** 이번 주 전적 읽기: 3승 1패(무승부가 있으면 1무를 더한다). */
internal fun weeklySpoken(weekly: BattleResult.Weekly): UiText = if (weekly.draws > 0) {
    uiText(R.string.battle_weekly_spoken_draw, weekly.wins, weekly.losses, weekly.draws)
} else {
    uiText(R.string.battle_weekly_spoken, weekly.wins, weekly.losses)
}

private const val SECONDS_PER_MINUTE = 60L

/**
 * 대결 결과(FR-093·FR-103·FR-108): BattleRepository.result 비교표·판정(상대 표시명·도달 시간 차)·적립 리워드·이번 주 전적을 보인다.
 * 결과를 보인 뒤 BattleRunCoordinator.acknowledgeFinished를 불러 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 한다.
 */
@HiltViewModel
class BattleResultViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(BattleResultUiState())
    val state: StateFlow<BattleResultUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(battleId: Long, isForced: Boolean = false) {
        if (!isForced && _state.value.battleId == battleId && _state.value.error == null) return
        job?.cancel()
        _state.update { it.copy(isLoading = true, battleId = battleId, error = null) }
        job = viewModelScope.launch {
            when (val result = battles.result(battleId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isLoading = false, result = result.value) }
                    coordinator.acknowledgeFinished()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }
}

data class BattleHistoryUiState(
    val isLoading: Boolean = true,
    val filterIndex: Int = 0,
    val history: BattleHistory? = null,
    /** 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)

/**
 * 함께 달린 기록(FR-101): 통계 + 종류별(전체·랜덤·나와의 싸움·유명인) 최신순 목록. 고스트는 랜덤으로 보인다(Q-02).
 * 끝까지 내리면 다음 쪽(nextCursor)을 이어 받는다.
 */
@HiltViewModel
class BattleHistoryViewModel @Inject constructor(private val battles: BattleRepository) : ViewModel() {
    private val _state = MutableStateFlow(BattleHistoryUiState())
    val state: StateFlow<BattleHistoryUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var moreJob: Job? = null

    init {
        load()
    }

    /** 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun loadMore() {
        val s = _state.value
        val history = s.history ?: return
        val cursor = history.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        val filter = HISTORY_FILTERS[s.filterIndex].second
        moreJob = viewModelScope.launch {
            when (val more = battles.history(filter, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val current = st.history ?: more.value
                    val known = current.entries.map { it.battleId }.toSet()
                    st.copy(
                        isLoadingMore = false,
                        history = current.copy(
                            entries = current.entries + more.value.entries.filterNot { it.battleId in known },
                            nextCursor = more.value.nextCursor
                        )
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = BattleErrorText.of(more.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onFilter(index: Int) {
        if (index == _state.value.filterIndex || index !in HISTORY_FILTERS.indices) return
        _state.update { it.copy(filterIndex = index) }
        load()
    }

    fun load() {
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val result = battles.history(HISTORY_FILTERS[_state.value.filterIndex].second)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, history = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }
}

/** 이력 거르기 라벨 → 종류(전체는 null). RANDOM이면 서버가 GHOST도 함께 준다. */
val HISTORY_FILTERS: List<Pair<Int, BattleHistory.Type?>> = listOf(
    R.string.battle_filter_all to null,
    R.string.battle_type_random to BattleHistory.Type.RANDOM,
    R.string.battle_type_self to BattleHistory.Type.SELF,
    R.string.battle_type_celebrity to BattleHistory.Type.CELEBRITY
)

@StringRes
internal fun typeLabel(type: BattleHistory.Type): Int = when (type) {
    BattleHistory.Type.RANDOM, BattleHistory.Type.GHOST -> R.string.battle_type_random
    BattleHistory.Type.SELF -> R.string.battle_type_self
    BattleHistory.Type.CELEBRITY -> R.string.battle_type_celebrity
}
