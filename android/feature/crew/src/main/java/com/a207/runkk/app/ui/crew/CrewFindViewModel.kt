package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.MyCrew
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루 탭(FR-117~FR-122, FR-124, FR-143). 들어오면 GET /crews/me로 소속을 확인해 크루가 있으면 내 크루 홈을, 없으면
 * 크루 찾기(대기 중인 내 가입 신청·취소 포함)를 보인다. 찾기는 공개 크루 목록(태그 AND·키워드, 끝까지 내리면 다음 쪽)과 추천을 보이고,
 * 상세 시트에서 바로 가입(자유)·가입 신청(승인제), 초대 코드 가입을 한다. 가입되면(JOINED) 그 자리에서 내 크루 홈으로 바뀐다.
 * 비공개 크루는 초대 코드로만 들어온다(Q-38).
 */
@HiltViewModel
class CrewFindViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewFindUiState())
    val state: StateFlow<CrewFindUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var moreJob: Job? = null
    private var mineJob: Job? = null
    private var detailJob: Job? = null

    /** 찾기 목록(첫 쪽)을 한 번이라도 읽기 시작했는가. 공개 크루가 0개여도 돌아올 때마다 다시 읽지 않게 한다(리뷰 N-12). */
    private var hasRequestedFind = false

    init {
        loadMine()
    }

    /**
     * 다른 화면(크루 만들기 등)에서 크루 탭으로 돌아왔다. 탭 항목과 ViewModel이 그대로 남아 있어(selectTab이 같은 키를 다시 넣는다)
     * 그 사이 생긴 소속을 모르므로 조용히 다시 확인한다. 이미 내 크루를 보이거나 처음 확인 중이면 하지 않는다(E2E 시나리오 5에서 찾은 결함).
     */
    fun refreshMine() {
        val s = _state.value
        if (s.mine == null && !s.isCheckingMine) loadMine(isQuiet = true)
    }

    /** 내 크루를 확인한다. [isQuiet]면 로딩을 보이지 않는다(가입·취소 뒤 다시 읽기). 크루가 없으면 찾기 목록도 읽는다. */
    fun loadMine(isQuiet: Boolean = false) {
        mineJob?.cancel()
        if (!isQuiet) _state.update { it.copy(isCheckingMine = true, mineError = null) }
        mineJob = viewModelScope.launch {
            when (val r = crews.myCrew()) {
                is Outcome.Success -> {
                    val crew = r.value.crew
                    val firstFind = !hasRequestedFind
                    _state.update {
                        it.copy(
                            isCheckingMine = false,
                            mineError = null,
                            mine = crew,
                            pending = r.value.pendingRequests
                        )
                    }
                    if (crew == null && (!isQuiet || firstFind)) load()
                }

                is Outcome.Failure -> _state.update {
                    if (isQuiet) {
                        it.copy(message = CrewErrorText.of(r.error))
                    } else {
                        it.copy(isCheckingMine = false, mineError = CrewErrorText.of(r.error))
                    }
                }
            }
        }
    }

    /** 첫 쪽부터 다시 찾는다. */
    fun load() {
        hasRequestedFind = true
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        val s = _state.value
        job = viewModelScope.launch {
            when (val found = crews.search(s.keyword.trim().ifBlank { null }, s.tags)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        crews = found.value.crews,
                        nextCursor = found.value.nextCursor
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = CrewErrorText.of(found.error))
                }
            }
        }
    }

    /** 목록 끝에 닿으면 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val more = crews.search(s.keyword.trim().ifBlank { null }, s.tags, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val known = st.crews.map { it.crewId }.toSet()
                    st.copy(
                        isLoadingMore = false,
                        crews = st.crews + more.value.crews.filterNot { it.crewId in known },
                        nextCursor = more.value.nextCursor
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = CrewErrorText.of(more.error))
                }
            }
        }
    }

    fun onKeyword(text: String) = _state.update { it.copy(keyword = text.take(KEYWORD_MAX)) }

    fun onTagToggle(id: String) {
        _state.update { it.copy(tags = RunkkTags.toggle(it.tags, id)) }
        load()
    }

    /** 상세를 연다. 지난 주 달린 거리는 크루 정보(GET /crews/{id})로 채운다. 읽지 못하면 빈 값으로 둔다. */
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

    /** 가입되면(JOINED) 내 크루 홈으로 바뀌고, 승인제면(PENDING) 신청 안내와 함께 대기 중 신청에 더한다(FR-120·FR-121). */
    fun onJoin(item: CrewList.Item) {
        if (_state.value.isJoining) return
        _state.update { it.copy(isJoining = true) }
        viewModelScope.launch {
            when (val result = crews.apply(item.crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isJoining = false, detail = null) }
                    onJoined(result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isJoining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    fun onInviteOpen() = _state.update { it.copy(isInviteOpen = true, inviteCode = "") }

    fun onInviteClose() = _state.update { it.copy(isInviteOpen = false) }

    /** 초대 코드는 6자리 영문·숫자(FR-112). 대문자로 맞춘다. */
    fun onInviteCode(text: String) = _state.update {
        it.copy(inviteCode = text.filter(Char::isLetterOrDigit).take(INVITE_LENGTH).uppercase())
    }

    /** 초대 코드 가입(FR-122): 가입되면 응답의 크루로 내 크루 홈을 연다. */
    fun onInviteSubmit() {
        val code = _state.value.inviteCode
        if (code.length != INVITE_LENGTH || _state.value.isJoining) return
        _state.update { it.copy(isJoining = true) }
        viewModelScope.launch {
            when (val result = crews.applyWithInviteCode(code)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isJoining = false, isInviteOpen = false) }
                    onJoined(result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isJoining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    // 대기 중인 내 가입 신청(FR-143)

    fun onCancelAsk(request: MyCrew.PendingJoin) = _state.update { it.copy(confirmCancel = request) }

    fun onCancelDismiss() = _state.update { it.copy(confirmCancel = null) }

    /** 확인 뒤 가입 신청 취소(DELETE /join-requests/{id}). */
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
                    // 이미 승인·거절됐을 수 있다(REQUEST_DECIDED·NOT_CANCELLABLE): 소속과 신청을 다시 읽는다.
                    loadMine(isQuiet = true)
                }
            }
        }
    }

    /** 가입 결과: 가입됐으면 소속을 다시 읽어 내 크루 홈으로, 신청이면 안내 뒤 대기 중 신청을 다시 읽는다. */
    private fun onJoined(result: JoinResult) {
        when (result.status) {
            JoinResult.Status.JOINED -> loadMine()

            JoinResult.Status.PENDING -> {
                _state.update { it.copy(message = uiText(R.string.crew_find_applied_message)) }
                loadMine(isQuiet = true)
            }

            JoinResult.Status.CANCELLED -> loadMine(isQuiet = true)
        }
    }

    private companion object {
        const val KEYWORD_MAX = 20
        const val INVITE_LENGTH = 6
    }
}
