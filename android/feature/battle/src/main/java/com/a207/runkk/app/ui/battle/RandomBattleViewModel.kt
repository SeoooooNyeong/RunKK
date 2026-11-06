package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
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
 * 랜덤 대결(FR-285, D-498): 확정 티어 회원이 거리를 골라 매칭한다. 열리면 본인 배치 진행(AccountRepository.placement)을 읽어 배치 중이면
 * 배치고사 진행(Routes.PLACEMENT)으로 바꿔 열고, 배치고사를 마쳤는데 그 완료 화면을 이 기기에서 아직 안 봤으면 완료 화면으로 바꿔 연다(PL3-A1:
 * 판 결과가 정리 중인 채 홈으로 나간 뒤 판이 세어져도 홈 랜덤 카드로 FR-281에 닿게). 아니면 본인 티어 배지를 둔다. 진행을 못 읽으면 배지 없이
 * 그대로 둔다(서버가 배치고사로 받는다).
 * 시작은 위치 동의(D-267)를 확인한 뒤 매칭(BattleQuickMatch)으로 간다. 남은 친구 초대는 매칭을 막으므로 돌아올 때마다 정리한다(D-271).
 */
@HiltViewModel
class RandomBattleViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val consent: LocationConsentUseCase,
    private val account: AccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RandomBattleUiState())
    val state: StateFlow<RandomBattleUiState> = _state.asStateFlow()

    private val _events = Channel<RandomBattleEvent>(Channel.BUFFERED)
    val events: Flow<RandomBattleEvent> = _events.receiveAsFlow()

    private var pending: RandomBattleEvent? = null
    private var consentJob: Job? = null
    private var recoverJob: Job? = null

    init {
        viewModelScope.launch { loadPlacement() }
        viewModelScope.launch { battles.preferredDistance()?.let { d -> _state.update { it.copy(distance = d) } } }
        viewModelScope.launch {
            val bests = battles.bestByDistance()
            _state.update { it.copy(bestByDistance = bests) }
        }
    }

    /** 화면에 돌아올 때마다 남은 초대를 확인한다(D-271). */
    fun onResumed() {
        if (recoverJob?.isActive == true) return
        recoverJob = viewModelScope.launch { coordinator.cancelLeftoverInvite(battles) }
    }

    fun onDistanceSelected(distance: Distance) {
        if (distance !in MatchUiState.DISTANCES) return
        _state.update { it.copy(distance = distance) }
    }

    /** 「n km 매칭 시작」. */
    fun onStartTapped() = request(RandomBattleEvent.Open(Routes.BattleQuickMatch(_state.value.distance.meters)))

    /** 위치 동의 시트 「동의하고 시작」: 동의를 주고 누른 동작을 이어 간다. 실패하면 시작하지 않고 알린다. */
    fun onConsentAgreed() {
        if (consentJob?.isActive == true) return
        _state.update { it.copy(isConsentSheetVisible = false) }
        consentJob = viewModelScope.launch {
            when (val given = consent.give()) {
                is Outcome.Success -> pending?.let(_events::trySend)
                is Outcome.Failure -> _state.update { it.copy(message = BattleErrorText.of(given.error)) }
            }
            pending = null
        }
    }

    fun onConsentClosed() {
        pending = null
        _state.update { it.copy(isConsentSheetVisible = false) }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun request(event: RandomBattleEvent) {
        if (consent.isGranted()) {
            _events.trySend(event)
        } else {
            pending = event
            _state.update { it.copy(isConsentSheetVisible = true) }
        }
    }

    private suspend fun loadPlacement() {
        val progress = (account.placement() as? Outcome.Success)?.value ?: return
        if (progress.status == PlacementStatus.IN_PROGRESS) {
            _events.trySend(RandomBattleEvent.Open(Routes.Key(Routes.PLACEMENT)))
            return
        }
        if (progress.isCompletionUnseen(account.placementCompleteSeen())) {
            _events.trySend(RandomBattleEvent.Open(Routes.Key(Routes.PLACEMENT_COMPLETE)))
            return
        }
        _state.update { it.copy(tierDisplay = TierDisplay.ofSelf(progress.tier, progress.provisionalTier)) }
    }
}
