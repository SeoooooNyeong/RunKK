package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.MyCrew
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
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
 * 크루 검색 화면 상태(피그마 F-01d·F-01e, Routes.CREW_SEARCH).
 *
 * @property keyword 입력한 크루명 검색어
 * @property results 찾은 공개 크루(최근 생성순)
 * @property resultCount 같은 검색어의 공개 크루 전체 수("검색 결과 [n]개", 서버 totalCount)
 * @property hasSearched 한 번이라도 찾았는가(찾기 전에는 결과 수·빈 화면을 보이지 않는다)
 */
data class CrewSearchUiState(
    val keyword: String = "",
    /** 마지막으로 찾은 검색어(다음 쪽은 입력 중인 값이 아니라 이것으로 잇는다). */
    val searchedKeyword: String? = null,
    val results: List<CrewList.Item> = emptyList(),
    val resultCount: Long = 0,
    val hasSearched: Boolean = false,
    val isLoading: Boolean = false,
    val nextCursor: Long? = null,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
    /** 상세로 연 크루와 그 정보(크루 찾기와 같은 상세 화면). */
    val detail: CrewList.Item? = null,
    val detailInfo: CrewDetail? = null,
    /** 대기 중인 본인 가입 신청(상세에서 취소). */
    val pending: List<MyCrew.PendingJoin> = emptyList(),
    val confirmCancel: MyCrew.PendingJoin? = null,
    val isJoining: Boolean = false,
    /** 24시간 가입 제한까지 남은 시간(F-02c). */
    val joinRemaining: Duration? = null,
    val message: UiText? = null
)

/** 크루 검색 화면의 한 번 이벤트. */
sealed interface CrewSearchEvent {
    /** 가입됐다: 크루 탭(내 크루 홈)으로 간다. */
    data object Joined : CrewSearchEvent
}

/**
 * 크루 검색(피그마 F-01d·F-01e): 크루명으로 공개 크루를 찾고(GET /crews?keyword=, 끝까지 내리면 다음 쪽) 결과 수는 서버 전체 수
 * (totalCount)로 보인다. 결과를 누르면 크루 찾기와 같은 상세에서 가입·가입 신청·신청 취소를 하고, 24시간 제한 중이면 남은 시간을
 * 보인다. 가입되면 크루 탭으로 돌아가 내 크루 홈을 연다.
 */
@HiltViewModel
class CrewSearchViewModel internal constructor(private val crews: CrewRepository, clock: Clock) : ViewModel() {
    @Inject
    constructor(crews: CrewRepository) : this(crews, Clock.systemUTC())

    private val _state = MutableStateFlow(CrewSearchUiState())
    val state: StateFlow<CrewSearchUiState> = _state.asStateFlow()

    private val _events = Channel<CrewSearchEvent>(Channel.BUFFERED)
    val events: Flow<CrewSearchEvent> = _events.receiveAsFlow()

    private var job: Job? = null
    private var moreJob: Job? = null
    private var detailJob: Job? = null
    private val cooldown = JoinCooldown(clock)

    init {
        loadMine()
    }

    /** 크루 로고 바이트(크루 이미지가 읽는다). */
    suspend fun logo(url: String): Outcome<ByteArray> = crews.logo(url)

    /** 대기 중인 신청과 24시간 제한 시각을 읽는다(상세 단추에 쓴다). 실패하면 빈 값으로 둔다(가입은 서버가 판정한다). */
    private fun loadMine() {
        viewModelScope.launch {
            val mine = (crews.myCrew() as? Outcome.Success)?.value ?: return@launch
            _state.update { it.copy(pending = mine.pendingRequests) }
            cooldown.start(viewModelScope, mine.joinableAt) { left -> _state.update { it.copy(joinRemaining = left) } }
        }
    }

    fun onKeyword(text: String) = _state.update { it.copy(keyword = text.take(KEYWORD_MAX)) }

    /** 검색어를 지운다(✕). 결과도 비운다. */
    fun onClear() {
        job?.cancel()
        moreJob?.cancel()
        _state.update {
            it.copy(
                keyword = "",
                searchedKeyword = null,
                results = emptyList(),
                resultCount = 0,
                hasSearched = false,
                nextCursor = null,
                isLoading = false,
                isLoadingMore = false,
                error = null
            )
        }
    }

    /** 키보드 검색: 빈 검색어는 찾지 않는다. */
    fun onSearch() {
        val keyword = _state.value.keyword.trim()
        if (keyword.isEmpty()) return
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val found = crews.search(keyword, emptySet())) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        searchedKeyword = keyword,
                        results = found.value.crews,
                        resultCount = found.value.totalCount,
                        nextCursor = found.value.nextCursor
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = CrewErrorText.of(found.error))
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        val keyword = s.searchedKeyword ?: return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val more = crews.search(keyword, emptySet(), cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val known = st.results.map { it.crewId }.toSet()
                    st.copy(
                        isLoadingMore = false,
                        results = st.results + more.value.crews.filterNot { it.crewId in known },
                        nextCursor = more.value.nextCursor
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = CrewErrorText.of(more.error))
                }
            }
        }
    }

    fun onOpen(item: CrewList.Item) {
        _state.update { it.copy(detail = item, detailInfo = null) }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val info = (crews.detail(item.crewId) as? Outcome.Success)?.value ?: return@launch
            _state.update { if (it.detail?.crewId == item.crewId) it.copy(detailInfo = info) else it }
        }
    }

    fun onCloseDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null, detailInfo = null) }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onJoin(item: CrewList.Item) {
        if (_state.value.isJoining) return
        _state.update { it.copy(isJoining = true) }
        viewModelScope.launch {
            when (val result = crews.apply(item.crewId)) {
                is Outcome.Success -> when (result.value.status) {
                    JoinResult.Status.JOINED -> {
                        _state.update { it.copy(isJoining = false, detail = null) }
                        _events.trySend(CrewSearchEvent.Joined)
                    }

                    else -> {
                        _state.update {
                            it.copy(isJoining = false, message = uiText(R.string.crew_find_applied_message))
                        }
                        loadMine()
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isJoining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    fun onCancelAsk(request: MyCrew.PendingJoin) = _state.update { it.copy(confirmCancel = request) }

    fun onCancelDismiss() = _state.update { it.copy(confirmCancel = null) }

    fun onCancelConfirm() {
        val request = _state.value.confirmCancel ?: return
        _state.update { it.copy(confirmCancel = null, isJoining = true) }
        viewModelScope.launch {
            when (val r = crews.cancelJoinRequest(request.requestId)) {
                is Outcome.Success -> _state.update { st ->
                    st.copy(
                        isJoining = false,
                        pending = st.pending.filterNot { it.requestId == request.requestId },
                        message = uiText(R.string.crew_find_cancelled_message, request.crewName)
                    )
                }

                is Outcome.Failure -> {
                    _state.update { it.copy(isJoining = false, message = CrewErrorText.of(r.error)) }
                    loadMine()
                }
            }
        }
    }

    private companion object {
        const val KEYWORD_MAX = 20
    }
}
