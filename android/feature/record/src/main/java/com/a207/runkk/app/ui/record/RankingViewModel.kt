package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 랭킹(FR-070, FR-290). 「나」 얼굴 원(내 순위 카드·목록의 내 줄)은 홈 아바타·마이와 같은 내 아바타 얼굴로 그린다(PM 2026-11-15):
 * 화면을 열 때 한 번 읽고, 읽지 못하면 다음 [refresh]에서 다시 읽는다.
 */
@HiltViewModel
class RankingViewModel internal constructor(
    private val records: RecordRepository,
    private val clock: Clock,
    /** 내 얼굴 읽기([myRankingFace]). 못 읽으면 null. */
    private val readMyFace: suspend () -> RankingFace? = { null }
) : ViewModel() {
    @Inject
    constructor(records: RecordRepository, clock: Clock, avatars: AvatarRepository) :
        this(records, clock, { avatars.myRankingFace() })

    private val _state = MutableStateFlow(RankingUiState(myFace = RankingFace.LOADING))
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    /** 내 얼굴 읽기. init보다 먼저 선언해 init이 시작한 읽기를 뒤의 초기값이 덮지 않게 한다. */
    private var myFaceJob: Job? = null

    /** 내 얼굴을 읽었다(다시 읽지 않는다). */
    private var hasMyFace = false

    /** 진행 중인 읽기. 범위·기간을 빨리 바꾸면 앞 읽기를 취소해 늦게 온 응답이 고른 범위·기간을 덮지 않게 한다(PL5-A1). */
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onScopeSelected(scope: RankingScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        refresh()
    }

    fun onPeriodSelected(period: AggregationPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    /** 받는 중이거나 받은 얼굴 키(같은 얼굴을 거듭 받지 않는다). 받기에 실패한 키는 빼서 다시 보일 때 다시 받는다. */
    private val requestedFaces = mutableSetOf<String>()

    /**
     * 얼굴 원이 화면에 보일 때(S15P31A207-300): 그 얼굴 키의 셀카 얼굴을 받는다. 목록이 최대 100줄이라 보이는 줄만 받는다.
     * 받을 키가 아니면([isFetchableFace], 기본 캐릭터·모르는 접두어) 받을 것이 없다. 받는 동안 같은 키는 거듭 받지 않고, 받지 못하면
     * 빈 층(기본 머리)으로 두되 「받음」에서 빼 다음에 보일 때(다시 시도·기간 바꾸기 포함) 한 번 더 받는다(AR-88, 시상대
     * SelfieHeads와 같은 정책). 보일 때마다 많아야 한 번이라 다시 받기가 몰리지 않는다.
     */
    fun onFaceShown(faceKey: String) {
        if (!isFetchableFace(faceKey)) return
        if (!requestedFaces.add(faceKey)) return
        viewModelScope.launch {
            val result = records.faceLayers(faceKey)
            if (result is Outcome.Failure) requestedFaces.remove(faceKey)
            val layers = result.getOrNull().orEmpty()
            _state.update { it.copy(faces = it.faces + (faceKey to layers)) }
        }
    }

    /** 내 얼굴을 한 번 읽는다. 읽지 못하면 모름(읽는 중 자리를 걷는다)으로 두고 다음 [refresh]에서 다시 읽는다. */
    private fun readMyFaceOnce() {
        if (hasMyFace || myFaceJob?.isActive == true) return
        myFaceJob = viewModelScope.launch {
            val face = readMyFace()
            if (face != null) hasMyFace = true
            _state.update { it.copy(myFace = face ?: it.myFace?.takeUnless(RankingFace::isLoading)) }
        }
    }

    fun refresh() {
        readMyFaceOnce()
        val s = _state.value
        _state.update {
            it.copy(isLoading = true, error = null, window = RankingWindow.of(it.period, clock.instant()))
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = when (s.scope) {
                RankingScope.PERSONAL -> records.personalRanking(s.period)
                RankingScope.CREW -> records.crewRanking(s.period)
            }
            when (result) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, ranking = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, ranking = null, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
