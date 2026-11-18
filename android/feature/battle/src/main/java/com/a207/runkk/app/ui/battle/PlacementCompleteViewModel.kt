package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.runimal.RunimalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * 배치고사 완료(FR-281, D-501): 본인 배치 진행(AccountRepository.placement)의 확정 티어와 센 세 판을 보인다. 완료를 읽으면 마지막 판을
 * 본 것으로 기기에 기억한다(AccountRepository.savePlacementCompleteSeen, D-531: 배치고사 진행 화면이 다시 열지 않게). 확정된 Runimal
 * 유형이 있으면 그 한 줄 설명을 한 번 읽는다(Runimal 카드).
 */
@HiltViewModel
class PlacementCompleteViewModel internal constructor(
    private val account: AccountRepository,
    /** 확정 Runimal 유형의 한 줄 설명(RunimalRepository.type). 못 읽으면 null. */
    private val describe: suspend (String) -> String?
) : ViewModel() {
    @Inject
    constructor(account: AccountRepository, runimals: RunimalRepository) :
        this(account, { type -> (runimals.type(type) as? Outcome.Success)?.value?.description })

    /** 설명을 읽지 않는 생성(시험). */
    internal constructor(account: AccountRepository) : this(account, { null })

    private val _state = MutableStateFlow(PlacementCompleteUiState())
    val state: StateFlow<PlacementCompleteUiState> = _state.asStateFlow()

    private val _events = Channel<PlacementCompleteEvent>(Channel.BUFFERED)
    val events: Flow<PlacementCompleteEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onRandomBattle() {
        _events.trySend(PlacementCompleteEvent.RandomBattle)
    }

    fun onHome() {
        _events.trySend(PlacementCompleteEvent.Home)
    }

    /** 왼쪽 위 뒤로·시스템 뒤로는 「홈으로」와 같다(8절 10, D-501). */
    fun onBack() = onHome()

    private fun load() {
        if (job?.isActive == true) return
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val read = account.placement()) {
                is Outcome.Success -> {
                    val rounds = read.value.rounds.sortedBy { it.round }
                    // 기기 저장이 실패해도 저장소가 이번 실행 동안 본 것으로 치고 남긴다(PL3-A2).
                    if (read.value.status == PlacementStatus.COMPLETED) {
                        rounds.lastOrNull()?.let { account.savePlacementCompleteSeen(it.battleId) }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            tier = read.value.tier,
                            rounds = rounds,
                            wins = rounds.count { r -> r.verdict == Verdict.WIN.name },
                            losses = rounds.count { r -> r.verdict == Verdict.LOSE.name },
                            draws = rounds.count { r -> r.verdict == Verdict.DRAW.name },
                            runimalType = read.value.runimalType
                        )
                    }
                    read.value.runimalType?.let { type ->
                        describe(type)?.let { text -> _state.update { it.copy(runimalDescription = text) } }
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = UiText.Res(R.string.placement_load_failed))
                }
            }
        }
    }
}
