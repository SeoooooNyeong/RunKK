package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.RandomBattleGate
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
 * 배틀런 화면(D-268): 랜덤 대결 | 친구 대결 분절과 거리 고르기. 열리면 서버 기본 대결 거리(GET /battles/match)를 고른 거리로 두고,
 * 거리 카드의 내 최고 기록은 셀프런 후보(1·3·5·10km)에서 읽는다. 앱이 강제 종료돼 남은 친구 초대가 있으면(GET …/mine 200) 그 초대를
 * 취소해 랜덤 매칭이 막히지 않게 한다(D-271). 시작 버튼은 위치 동의(D-267)를 확인한 뒤 매칭 대기·초대 대기 화면으로 간다.
 */
@HiltViewModel
class BattleModeViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val consent: LocationConsentUseCase,
    private val account: AccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BattleModeUiState())
    val state: StateFlow<BattleModeUiState> = _state.asStateFlow()

    private val _events = Channel<BattleModeEvent>(Channel.BUFFERED)
    val events: Flow<BattleModeEvent> = _events.receiveAsFlow()

    private var pending: BattleModeEvent? = null
    private var consentJob: Job? = null
    private var recoverJob: Job? = null

    init {
        viewModelScope.launch { loadDefaultDistance() }
        viewModelScope.launch { loadBests() }
        viewModelScope.launch { loadRandomLock() }
    }

    /** 화면에 돌아올 때마다(초대 대기에서 돌아옴 포함) 남은 초대를 확인한다. */
    fun onResumed() {
        if (recoverJob?.isActive == true) return
        recoverJob = viewModelScope.launch { recoverInvite() }
    }

    fun onSegmentSelected(segment: BattleModeUiState.Segment) = _state.update { it.copy(segment = segment) }

    fun onDistanceSelected(distance: Distance) {
        if (distance !in MatchUiState.DISTANCES) return
        _state.update { it.copy(distance = distance) }
    }

    /** "n km 랜덤 매칭 시작". 언랭이면 매칭 대신 남은 유효 러닝 수를 알린다. */
    fun onRandomStartTapped() {
        if (_state.value.randomRunsLeft != null) {
            _state.update { it.copy(isRandomLockNoticeVisible = true) }
            return
        }
        request(BattleModeEvent.Open(Routes.BattleQuickMatch(_state.value.distance.meters)))
    }

    fun onRandomLockNoticeClosed() = _state.update { it.copy(isRandomLockNoticeVisible = false) }

    /** "카카오톡으로 친구 초대". */
    fun onInviteTapped() = request(BattleModeEvent.Open(Routes.FriendInviteWait(_state.value.distance.meters)))

    /** 위치 동의 시트 "동의하고 시작": 동의를 주고 누른 동작을 이어 간다. 실패하면 시작하지 않고 알린다. */
    fun onConsentAgreed() {
        if (consentJob?.isActive == true) return
        _state.update { it.copy(isConsentSheetVisible = false) }
        consentJob = viewModelScope.launch {
            when (val given = consent.give()) {
                is Outcome.Success -> pending?.let { open(it) }
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

    private fun request(event: BattleModeEvent) {
        if (consent.isGranted()) {
            open(event)
        } else {
            pending = event
            _state.update { it.copy(isConsentSheetVisible = true) }
        }
    }

    /**
     * 매칭·초대 대기로 넘어간다. 초대 대기로 갈 때만 아직 도는 남은 초대 조회를 멈춘다(AN8-22): 조회가 늦게 끝나면 방금 만든 새 초대를
     * 남은 초대로 보고 지울 수 있다. 랜덤 매칭은 남은 초대가 매칭을 막으므로 조회·정리를 끝까지 둔다.
     */
    private fun open(event: BattleModeEvent) {
        if ((event as? BattleModeEvent.Open)?.key is Routes.FriendInviteWait) recoverJob?.cancel()
        _events.trySend(event)
    }

    private suspend fun loadDefaultDistance() {
        val status = (battles.matchStatus() as? Outcome.Success)?.value ?: return
        val preferred = (if (status.isWaiting) status.distance else status.defaultDistance)
            ?.takeIf { it in MatchUiState.DISTANCES } ?: return
        _state.update { it.copy(distance = preferred) }
    }

    /** 거리별 내 최고 기록(셀프런 후보, 서버 D-238이 1km를 더했다). 못 읽으면 "기록 없음"으로 둔다. */
    private suspend fun loadBests() {
        val candidates = (battles.selfCandidates() as? Outcome.Success)?.value?.candidates ?: return
        val cards = MatchUiState.DISTANCES.map { distance ->
            val best = candidates.firstOrNull { it.distance == distance }
            BattleModeUiState.DistanceCard(distance, best?.finishTime, best?.recordedOn)
        }
        _state.update { it.copy(cards = cards) }
    }

    /**
     * 본인 요약의 티어와 유효 러닝 수(본인 프로필 runCount, D-331)로 랜덤 대결 잠금을 정한다. 요약을 못 읽으면 잠그지 않는다
     * (서버가 RANKED_ONLY로 막는다).
     */
    private suspend fun loadRandomLock() {
        val me = account.mySummary().getOrNull()
        _state.update { it.copy(randomRunsLeft = RandomBattleGate.runsLeft(me?.tier, me?.validRuns)) }
    }

    /** 앱이 강제 종료돼 남은 초대(최대 5분 동안 랜덤 매칭을 막는다)를 취소한다(D-271). */
    private suspend fun recoverInvite() {
        val leftover = (battles.myFriendInvite() as? Outcome.Success)?.value ?: return
        coordinator.cancelInvite(leftover.inviteId).await()
    }
}
