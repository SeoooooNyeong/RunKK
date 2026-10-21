package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.MyCrew
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- 크루 탭: 내 크루 확인 → 크루 찾기·상세 ----------

data class CrewFindUiState(
    /** 크루 탭에 들어와 내 크루(GET /crews/me)를 확인하는 중. */
    val checkingMine: Boolean = true,
    /** 소속 크루. 있으면 크루 탭이 내 크루 홈을 보인다. */
    val mine: CrewDetail? = null,
    /** 내 크루를 확인하지 못했다(다시 시도). 소속을 모르는 채 가입을 권하지 않도록 찾기 화면을 보이지 않는다. */
    val mineError: String? = null,
    /** 대기 중인 본인 가입 신청(FR-143 취소). */
    val pending: List<MyCrew.PendingJoin> = emptyList(),
    /** 취소를 확인 중인 신청. */
    val confirmCancel: MyCrew.PendingJoin? = null,
    val loading: Boolean = true,
    val keyword: String = "",
    val tags: Set<String> = emptySet(),
    /** 추천(FR-118). 태그가 없으면 서버가 빈 목록을 준다(FR-119). */
    val recommendations: List<CrewList.Item> = emptyList(),
    val crews: List<CrewList.Item> = emptyList(),
    /** 다음 쪽 커서. null이면 끝까지 읽었다. */
    val nextCursor: Long? = null,
    val loadingMore: Boolean = false,
    /** 상세(바텀 시트)로 연 크루. */
    val detail: CrewList.Item? = null,
    val joining: Boolean = false,
    val inviteOpen: Boolean = false,
    val inviteCode: String = "",
    val message: String? = null,
    val error: String? = null
)

sealed interface CrewEvent {
    /** 크루를 만들었다: 크루 탭을 새로 열어 내 크루 홈을 보인다. */
    data object Created : CrewEvent
}

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

    init {
        loadMine()
    }

    /** 내 크루를 확인한다. [quiet]면 로딩을 보이지 않는다(가입·취소 뒤 다시 읽기). 크루가 없으면 찾기 목록도 읽는다. */
    fun loadMine(quiet: Boolean = false) {
        mineJob?.cancel()
        if (!quiet) _state.update { it.copy(checkingMine = true, mineError = null) }
        mineJob = viewModelScope.launch {
            when (val r = crews.myCrew()) {
                is Outcome.Success -> {
                    val crew = r.value.crew
                    val firstFind = _state.value.crews.isEmpty() && _state.value.error == null
                    _state.update {
                        it.copy(checkingMine = false, mineError = null, mine = crew, pending = r.value.pendingRequests)
                    }
                    if (crew == null && (!quiet || firstFind)) load()
                }

                is Outcome.Failure -> _state.update {
                    if (quiet) {
                        it.copy(message = CrewErrorText.of(r.error))
                    } else {
                        it.copy(checkingMine = false, mineError = CrewErrorText.of(r.error))
                    }
                }
            }
        }
    }

    /** 첫 쪽부터 다시 찾는다. */
    fun load() {
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(loading = true, loadingMore = false, error = null) }
        val s = _state.value
        job = viewModelScope.launch {
            val recommended = async { crews.recommend() }
            when (val found = crews.search(s.keyword.trim().ifBlank { null }, s.tags)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        crews = found.value.crews,
                        nextCursor = found.value.nextCursor,
                        recommendations = recommended.await().getOrNull()?.crews.orEmpty()
                    )
                }

                is Outcome.Failure -> {
                    recommended.cancel()
                    _state.update { it.copy(loading = false, error = CrewErrorText.of(found.error)) }
                }
            }
        }
    }

    /** 목록 끝에 닿으면 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loading || s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val more = crews.search(s.keyword.trim().ifBlank { null }, s.tags, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val known = st.crews.map { it.crewId }.toSet()
                    st.copy(
                        loadingMore = false,
                        crews = st.crews + more.value.crews.filterNot { it.crewId in known },
                        nextCursor = more.value.nextCursor
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loadingMore = false, message = CrewErrorText.of(more.error))
                }
            }
        }
    }

    fun onKeyword(text: String) = _state.update { it.copy(keyword = text.take(KEYWORD_MAX)) }

    fun onTagToggle(id: String) {
        _state.update { it.copy(tags = RunkkTags.toggle(it.tags, id)) }
        load()
    }

    fun onOpen(item: CrewList.Item) = _state.update { it.copy(detail = item) }

    fun onCloseDetail() = _state.update { it.copy(detail = null) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** 가입되면(JOINED) 내 크루 홈으로 바뀌고, 승인제면(PENDING) 신청 안내와 함께 대기 중 신청에 더한다(FR-120·FR-121). */
    fun onJoin(item: CrewList.Item) {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = crews.apply(item.crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(joining = false, detail = null) }
                    onJoined(result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(joining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    fun onInviteOpen() = _state.update { it.copy(inviteOpen = true, inviteCode = "") }

    fun onInviteClose() = _state.update { it.copy(inviteOpen = false) }

    /** 초대 코드는 6자리 영문·숫자(FR-112). 대문자로 맞춘다. */
    fun onInviteCode(text: String) = _state.update {
        it.copy(inviteCode = text.filter(Char::isLetterOrDigit).take(INVITE_LENGTH).uppercase())
    }

    /** 초대 코드 가입(FR-122): 가입되면 응답의 크루로 내 크루 홈을 연다. */
    fun onInviteSubmit() {
        val code = _state.value.inviteCode
        if (code.length != INVITE_LENGTH || _state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = crews.applyWithInviteCode(code)) {
                is Outcome.Success -> {
                    _state.update { it.copy(joining = false, inviteOpen = false) }
                    onJoined(result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(joining = false, message = CrewErrorText.of(result.error))
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
        _state.update { it.copy(confirmCancel = null, joining = true) }
        viewModelScope.launch {
            when (val r = crews.cancelJoinRequest(request.requestId)) {
                is Outcome.Success -> _state.update { st ->
                    st.copy(
                        joining = false,
                        pending = st.pending.filterNot { it.requestId == request.requestId },
                        message = "${request.crewName} 가입 신청을 취소했어요"
                    )
                }

                is Outcome.Failure -> {
                    _state.update { it.copy(joining = false, message = CrewErrorText.of(r.error)) }
                    // 이미 승인·거절됐을 수 있다(REQUEST_DECIDED·NOT_CANCELLABLE): 소속과 신청을 다시 읽는다.
                    loadMine(quiet = true)
                }
            }
        }
    }

    /** 가입 결과: 가입됐으면 소속을 다시 읽어 내 크루 홈으로, 신청이면 안내 뒤 대기 중 신청을 다시 읽는다. */
    private fun onJoined(result: JoinResult) {
        when (result.status) {
            JoinResult.Status.JOINED -> loadMine()

            JoinResult.Status.PENDING -> {
                _state.update { it.copy(message = "가입 신청했어요. 크루장이 결정하면 알려 드려요") }
                loadMine(quiet = true)
            }

            JoinResult.Status.CANCELLED -> loadMine(quiet = true)
        }
    }

    private companion object {
        const val KEYWORD_MAX = 20
        const val INVITE_LENGTH = 6
    }
}

// ---------- 크루 만들기 ----------

data class CrewFormState(
    val name: String = "",
    val intro: String = "",
    val tags: Set<String> = emptySet(),
    val visibility: CrewVisibility = CrewVisibility.PUBLIC,
    val joinPolicy: JoinPolicy = JoinPolicy.APPROVAL
) {
    /** 크루명 2~20자, 태그 1~5개(FR-110). 최종 판정은 서버가 한다. */
    val valid: Boolean get() = name.trim().length in NAME_MIN..NAME_MAX && tags.size in 1..RunkkTags.MAX_SELECTED

    fun toForm() = CrewForm(name.trim(), intro.trim().ifBlank { null }, tags, visibility, joinPolicy)

    companion object {
        const val NAME_MIN = 2
        const val NAME_MAX = 20
        const val INTRO_MAX = 40
    }
}

data class CrewCreateUiState(
    val form: CrewFormState = CrewFormState(),
    val saving: Boolean = false,
    val error: String? = null
)

/** 크루 만들기(FR-110): 만든 러너가 크루장이 된다. 만들면 크루 탭(내 크루 홈)으로. */
@HiltViewModel
class CrewCreateViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewCreateUiState())
    val state: StateFlow<CrewCreateUiState> = _state.asStateFlow()

    private val _events = Channel<CrewEvent>(Channel.BUFFERED)
    val events: Flow<CrewEvent> = _events.receiveAsFlow()

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form, error = null) }

    fun onSubmit() {
        val s = _state.value
        if (!s.form.valid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = crews.create(s.form.toForm())) {
                is Outcome.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.trySend(CrewEvent.Created)
                }

                is Outcome.Failure -> _state.update { it.copy(saving = false, error = CrewErrorText.of(result.error)) }
            }
        }
    }
}
