package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.PlacementProgress
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
 * 배치고사 진행(FR-279, D-479·D-498): 배치 중 회원의 랜덤 대결. 본인 배치 진행을 읽어 진행 카드에 보이고, 「배치고사 n판 시작」은 위치 동의를
 * 확인한 뒤 확정 티어와 같은 매칭 흐름(BattleQuickMatch)을 연다(서버가 대기 없이 배치고사 대결을 만든다). 진행이 배치 중이 아니면(이미 확정·면제)
 * 랜덤 대결로 바꿔 연다. 다만 배치고사를 마쳤는데 그 완료 화면을 아직 안 봤으면(기기 사본의 마지막 판이 다르면) 완료 화면으로 바꿔 연다
 * (PL2-A2: 판 결과가 판 셈을 30초 안에 받지 못해도 완료 화면에 닿게). 화면에 돌아올 때마다(판을 마치고 다시 열림) 진행을 다시 읽고 남은
 * 친구 초대를 정리한다(D-271).
 */
@HiltViewModel
class PlacementViewModel @Inject constructor(
    private val account: AccountRepository,
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val consent: LocationConsentUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(PlacementUiState())
    val state: StateFlow<PlacementUiState> = _state.asStateFlow()

    private val _events = Channel<PlacementEvent>(Channel.BUFFERED)
    val events: Flow<PlacementEvent> = _events.receiveAsFlow()

    private var pending: PlacementEvent? = null
    private var consentJob: Job? = null
    private var recoverJob: Job? = null
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { battles.preferredDistance()?.let { d -> _state.update { it.copy(distance = d) } } }
    }

    /** 화면에 돌아올 때마다(매칭을 그만두고 돌아옴 포함) 진행을 다시 읽고 남은 초대를 정리한다. */
    fun onResumed() {
        load()
        if (recoverJob?.isActive !=
            true
        ) {
            recoverJob = viewModelScope.launch { coordinator.cancelLeftoverInvite(battles) }
        }
    }

    /** 진행을 다시 읽는다(오류 뒤 다시 시도). */
    fun onRetry() = load()

    fun onDistanceSelected(distance: Distance) {
        if (distance !in MatchUiState.DISTANCES) return
        _state.update { it.copy(distance = distance) }
    }

    /** 「배치고사 n판 시작」. 진행을 못 읽었으면 아무것도 하지 않는다. */
    fun onStartTapped() {
        if (!_state.value.canStart) return
        val event = PlacementEvent.Open(Routes.BattleQuickMatch(_state.value.distance.meters))
        if (consent.isGranted()) {
            _events.trySend(event)
        } else {
            pending = event
            _state.update { it.copy(isConsentSheetVisible = true) }
        }
    }

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

    private fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val read = account.placement()) {
                is Outcome.Success -> {
                    if (read.value.status != PlacementStatus.IN_PROGRESS) {
                        _events.trySend(PlacementEvent.Open(Routes.Key(settledRoute(read.value))))
                    }
                    _state.update { it.copy(isLoading = false, progress = read.value) }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = UiText.Res(R.string.placement_load_failed))
                }
            }
        }
    }

    /** 배치 중이 아닐 때 갈 곳: 마쳤고 마지막 판의 완료 화면을 아직 안 봤으면 완료 화면, 아니면(봤음·면제) 랜덤 대결. */
    private suspend fun settledRoute(progress: PlacementProgress): String {
        val last = progress.rounds.maxByOrNull { it.round }?.battleId
        val isUnseen = progress.status == PlacementStatus.COMPLETED && last != null &&
            account.placementCompleteSeen() != last
        return if (isUnseen) Routes.PLACEMENT_COMPLETE else Routes.BATTLE_RANDOM
    }
}
