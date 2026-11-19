package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.battle.BattleHistory
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
 * 친구 대결(FR-286, D-498): 거리를 골라 카카오톡으로 초대한다(초대 대기 FriendInviteWait). 처음 거리는 기본 대결 거리(FR-109)다.
 * 최근 함께 달린 친구는 친구 대결 이력 첫 쪽(history(FRIEND))에서 고른다(D-492). 앱이 강제 종료돼 남은 초대는 돌아올 때마다 정리하고,
 * 초대 대기로 갈 때는 그 정리를 멈춘다(AN8-22: 늦게 끝난 정리가 방금 만든 초대를 지우지 않게).
 */
@HiltViewModel
class FriendBattleViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val consent: LocationConsentUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(FriendBattleUiState())
    val state: StateFlow<FriendBattleUiState> = _state.asStateFlow()

    private val _events = Channel<FriendBattleEvent>(Channel.BUFFERED)
    val events: Flow<FriendBattleEvent> = _events.receiveAsFlow()

    private var pending: FriendBattleEvent? = null
    private var consentJob: Job? = null
    private var recoverJob: Job? = null

    init {
        viewModelScope.launch { battles.preferredDistance()?.let { d -> _state.update { it.copy(distance = d) } } }
        viewModelScope.launch {
            // 받아 둔 최근 친구(시작 로딩이 남긴 사본, S15P31A207-345 2차)를 먼저 그리고 뒤에서 새로 받아 바꾼다.
            battles.cachedHistory(BattleHistory.Type.FRIEND)?.let { copy ->
                _state.update { if (it.friends.isEmpty()) it.copy(friends = RecentFriends.of(copy.entries)) else it }
            }
            val history = (battles.history(BattleHistory.Type.FRIEND) as? Outcome.Success)?.value ?: return@launch
            _state.update { it.copy(friends = RecentFriends.of(history.entries)) }
        }
    }

    fun onResumed() {
        if (recoverJob?.isActive == true) return
        recoverJob = viewModelScope.launch { coordinator.cancelLeftoverInvite(battles) }
    }

    /** 친구 고르기(선택만, 초대에 묶지 않는다). 다시 누르면 그대로 고른 상태다(라디오). */
    fun onFriendSelected(memberId: Long) = _state.update { it.copy(selectedFriendId = memberId) }

    fun onDistanceMenuToggle() = _state.update { it.copy(isDistanceMenuOpen = !it.isDistanceMenuOpen) }

    fun onDistanceMenuDismissed() = _state.update { it.copy(isDistanceMenuOpen = false) }

    fun onDistanceSelected(distance: Distance) {
        if (distance !in MatchUiState.DISTANCES) return
        _state.update { it.copy(distance = distance, isDistanceMenuOpen = false) }
    }

    /** 「카카오톡으로 초대하기」. 초대 요청에는 고른 친구를 넣지 않는다(초대 링크를 먼저 연 한 사람이 참가, FR-229). */
    fun onInviteTapped() {
        val event = FriendBattleEvent.Open(Routes.FriendInviteWait(_state.value.distance.meters))
        if (consent.isGranted()) {
            open(event)
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
                is Outcome.Success -> pending?.let(::open)
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

    private fun open(event: FriendBattleEvent) {
        recoverJob?.cancel()
        _events.trySend(event)
    }
}
