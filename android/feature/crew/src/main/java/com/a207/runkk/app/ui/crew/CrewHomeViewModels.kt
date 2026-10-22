package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 크루 홈 탭(시안 HiMyCrew): 레이스·크루런·응원보드·크루원. */
enum class CrewTab(@StringRes val labelRes: Int) {
    RACE(R.string.crew_home_tab_race),
    CREW_RUN(R.string.crew_home_tab_crew_run),
    CHEER(R.string.crew_home_tab_cheer),
    MEMBERS(R.string.crew_home_tab_members)
}

data class CrewHomeUiState(
    val crewId: Long = 0,
    /** 크루 정보(GET /crews/{id}): 이름·인원·내 역할. 읽기 전이면 null. */
    val detail: CrewDetail? = null,
    val tab: CrewTab = CrewTab.RACE,
    val isLoading: Boolean = false,
    val race: RaceStatus? = null,
    val period: CrewRanking.Period = CrewRanking.Period.WEEKLY,
    val ranking: CrewRanking? = null,
    val crewRuns: PostList? = null,
    /** 크루런 모집글별 대기실 단추 상태(postId → LobbyAvailability). 서버 시각 추정으로 1초마다 다시 판단한다. */
    val lobbyEntries: Map<Long, LobbyAvailability> = emptyMap(),
    val cheers: PostList? = null,
    val members: CrewMembers? = null,
    val cheerDraft: String = "",
    /** 게시글 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val isConfirmingLeave: Boolean = false,
    /** 지울지 확인 중인 내 글(FR-130·FR-134). */
    val confirmDelete: PostList.Item? = null,
    val isEditingIntro: Boolean = false,
    val introDraft: String = "",
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val isCheerValid: Boolean get() = cheerDraft.isNotBlank() && cheerDraft.length <= CHEER_MAX

    val crewName: String get() = detail?.name.orEmpty()

    /** 크루 관리 메뉴는 크루장에게만 보인다(myRole = LEADER). */
    val canManage: Boolean get() = detail?.isLeader == true

    companion object {
        const val CHEER_MAX = 200
        const val INTRO_MAX = 40
    }
}

sealed interface CrewHomeEvent {
    /** 크루를 나왔다: 크루 탭(찾기)으로. */
    data object Left : CrewHomeEvent
}

/**
 * 내 크루 홈(FR-125, FR-129~FR-131, FR-133, FR-135, FR-137, FR-140, FR-144, FR-145).
 * 크루 이름·인원·내 역할은 detail(GET /crews/{id})로 읽고, 탭을 처음 열 때 그 탭의 포트만 부른다(레이스 race + crewRanking,
 * 크루런 posts(CREW_RUN), 응원보드 posts(CHEER), 크루원 members). 게시글은 끝까지 내리면 다음 쪽(nextCursor)을 이어 받는다.
 * 크루런 모집글의 대기실 단추는 서버 시각 추정(ServerClock)으로 열림 30분 전·모집 일시를 따져 열고 닫는다(FR-133·FR-147).
 */
@HiltViewModel
class CrewHomeViewModel @Inject constructor(private val crews: CrewRepository, private val serverClock: ServerClock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewHomeUiState())
    val state: StateFlow<CrewHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.map { it.crewRuns }.distinctUntilChanged().collectLatest(::watchLobbies)
        }
    }

    private val _events = Channel<CrewHomeEvent>(Channel.BUFFERED)
    val events: Flow<CrewHomeEvent> = _events.receiveAsFlow()

    private var bound: Long? = null
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var moreJob: Job? = null

    fun bind(key: Routes.CrewHome) {
        if (bound == key.crewId) return
        bound = key.crewId
        _state.value = CrewHomeUiState(crewId = key.crewId)
        loadDetail()
        refresh()
    }

    private var resumedOnce = false

    /** 화면에 돌아올 때(관리·모집에서 복귀) 크루 정보와 지금 탭을 다시 읽는다. 처음 표시는 bind가 읽는다. */
    fun onResumed() {
        if (resumedOnce && bound != null) {
            loadDetail()
            refresh()
        }
        resumedOnce = true
    }

    /** 크루 정보(이름·인원·내 역할). 실패하면 이름 칸을 비워 두고 안내한다(탭 내용은 따로 읽는다). */
    private fun loadDetail() {
        val crewId = _state.value.crewId
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            when (val r = crews.detail(crewId)) {
                is Outcome.Success -> _state.update { it.copy(detail = r.value) }
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onTab(tab: CrewTab) {
        _state.update { it.copy(tab = tab) }
        val s = _state.value
        val loaded = when (tab) {
            CrewTab.RACE -> s.race != null
            CrewTab.CREW_RUN -> s.crewRuns != null
            CrewTab.CHEER -> s.cheers != null
            CrewTab.MEMBERS -> s.members != null
        }
        if (!loaded) refresh()
    }

    /** 지금 탭을 다시 읽는다. */
    fun refresh() {
        val s = _state.value
        refreshJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        refreshJob = viewModelScope.launch {
            val error = when (s.tab) {
                CrewTab.RACE -> {
                    val race = crews.race(s.crewId, null)
                    val ranking = crews.crewRanking(s.crewId, s.period)
                    _state.update {
                        it.copy(
                            race = (race as? Outcome.Success)?.value ?: it.race,
                            ranking =
                                (ranking as? Outcome.Success)?.value ?: it.ranking
                        )
                    }
                    (race as? Outcome.Failure)?.error ?: (ranking as? Outcome.Failure)?.error
                }

                CrewTab.CREW_RUN -> crews.posts(s.crewId, PostList.Kind.CREW_RUN).also { r ->
                    if (r is Outcome.Success) _state.update { it.copy(crewRuns = r.value) }
                }.let { (it as? Outcome.Failure)?.error }

                CrewTab.CHEER -> crews.posts(s.crewId, PostList.Kind.CHEER).also { r ->
                    if (r is Outcome.Success) _state.update { it.copy(cheers = r.value) }
                }.let { (it as? Outcome.Failure)?.error }

                CrewTab.MEMBERS -> crews.members(s.crewId).also { r ->
                    if (r is Outcome.Success) _state.update { it.copy(members = r.value) }
                }.let { (it as? Outcome.Failure)?.error }
            }
            _state.update { it.copy(isLoading = false, error = error?.let(CrewErrorText::of)) }
        }
    }

    fun onPeriod(period: CrewRanking.Period) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        val crewId = _state.value.crewId
        viewModelScope.launch {
            when (val r = crews.crewRanking(crewId, period)) {
                is Outcome.Success -> _state.update { it.copy(ranking = r.value) }
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    // 응원보드

    fun onCheerDraft(text: String) = _state.update { it.copy(cheerDraft = text.take(CrewHomeUiState.CHEER_MAX)) }

    fun onCheerSubmit() {
        val s = _state.value
        if (!s.isCheerValid || s.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.writeCheer(s.crewId, s.cheerDraft.trim())) {
                is Outcome.Success -> {
                    _state.update { it.copy(isBusy = false, cheerDraft = "") }
                    reloadPosts(PostList.Kind.CHEER)
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 삭제를 누르면 확인을 받는다(되돌릴 수 없다). */
    fun onDeleteAsk(item: PostList.Item) {
        if (item.isMine) _state.update { it.copy(confirmDelete = item) }
    }

    fun onDeleteDismiss() = _state.update { it.copy(confirmDelete = null) }

    fun onDeleteConfirm() {
        val item = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        onDeletePost(item)
    }

    /** 글 삭제(작성자 본인, FR-130·FR-134). 크루장의 남의 글 삭제는 크루 관리에서 권한을 확인한 뒤 서버가 판정한다. */
    fun onDeletePost(item: PostList.Item) {
        if (!item.isMine || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.deletePost(item.postId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isBusy = false, message = uiText(R.string.crew_home_post_deleted)) }
                    reloadPosts(item.kind)
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    // 크루런

    /**
     * 갈래요·못 가요(FR-145). 마감 전에는 바꿀 수 있다. 보낸 응답을 바로 그 글의 내 응답으로 보이고, 서버 목록을 다시 읽어
     * 참여 인원·내 응답(crewRun.myAnswer)을 맞춘다.
     */
    fun onVote(item: PostList.Item, answer: VoteAnswer) {
        viewModelScope.launch {
            when (val r = crews.vote(item.postId, answer)) {
                is Outcome.Success -> {
                    _state.update { st ->
                        st.copy(crewRuns = st.crewRuns?.withAnswer(item.postId, answer))
                    }
                    reloadPosts(PostList.Kind.CREW_RUN)
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 지금 탭(크루런·응원보드)의 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun onLoadMore() {
        val s = _state.value
        val kind = when (s.tab) {
            CrewTab.CREW_RUN -> PostList.Kind.CREW_RUN
            CrewTab.CHEER -> PostList.Kind.CHEER
            else -> return
        }
        val list = if (kind == PostList.Kind.CHEER) s.cheers else s.crewRuns
        val cursor = list?.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val r = crews.posts(s.crewId, kind, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val current = (if (kind == PostList.Kind.CHEER) st.cheers else st.crewRuns) ?: r.value
                    val known = current.posts.map { it.postId }.toSet()
                    val merged =
                        PostList(current.posts + r.value.posts.filterNot { it.postId in known }, r.value.nextCursor)
                    if (kind == PostList.Kind.CHEER) {
                        st.copy(isLoadingMore = false, cheers = merged)
                    } else {
                        st.copy(isLoadingMore = false, crewRuns = merged)
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error))
                }
            }
        }
    }

    // 크루원·탈퇴·내 소개

    fun onLeaveAsk() = _state.update { it.copy(isConfirmingLeave = true) }

    fun onLeaveDismiss() = _state.update { it.copy(isConfirmingLeave = false) }

    /** 확인 뒤 탈퇴(FR-125). 크루원이 남은 크루장이면 서버가 양도를 안내한다(FR-126). */
    fun onLeaveConfirm() {
        val crewId = _state.value.crewId
        _state.update { it.copy(isConfirmingLeave = false, isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.leave(crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isBusy = false) }
                    _events.trySend(CrewHomeEvent.Left)
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onIntroEdit() = _state.update { it.copy(isEditingIntro = true, introDraft = "") }

    fun onIntroDraft(text: String) = _state.update { it.copy(introDraft = text.take(CrewHomeUiState.INTRO_MAX)) }

    fun onIntroDismiss() = _state.update { it.copy(isEditingIntro = false) }

    /** 크루 내 한줄 소개(FR-144, 40자). */
    fun onIntroSave() {
        val s = _state.value
        _state.update { it.copy(isEditingIntro = false, isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.editCrewProfile(s.crewId, s.introDraft.trim())) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(isBusy = false, message = uiText(R.string.crew_home_intro_changed), members = null)
                    }
                    if (_state.value.tab == CrewTab.MEMBERS) refresh()
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /**
     * 모집글마다 대기실 단추 상태를 정하고, 시각이 지나 바뀔 수 있는 글(열리기 전·열림)이 남아 있는 동안 1초마다 다시 정한다.
     * 목록을 다시 받으면 collectLatest가 이 반복을 새로 시작한다. 바뀐 것이 없으면 상태를 내보내지 않는다.
     */
    private suspend fun watchLobbies(runs: PostList?) {
        val infos = runs?.posts.orEmpty().mapNotNull { post -> post.crewRun?.let { post.postId to it } }
        while (true) {
            val now = serverClock.now()
            val entries = infos.associate { (postId, info) -> postId to LobbyAvailability.of(info, now) }
            _state.update { if (it.lobbyEntries == entries) it else it.copy(lobbyEntries = entries) }
            if (entries.values.all { it == LobbyAvailability.Closed }) return
            delay(LOBBY_TICK_MILLIS)
        }
    }

    private suspend fun reloadPosts(kind: PostList.Kind) {
        moreJob?.cancel()
        _state.update { it.copy(isLoadingMore = false) }
        when (val r = crews.posts(_state.value.crewId, kind)) {
            is Outcome.Success -> _state.update {
                if (kind == PostList.Kind.CHEER) it.copy(cheers = r.value) else it.copy(crewRuns = r.value)
            }

            is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
        }
    }
}

private const val LOBBY_TICK_MILLIS = 1_000L

/** [postId] 모집글의 내 응답을 [answer]로 바꾼 목록. */
private fun PostList.withAnswer(postId: Long, answer: VoteAnswer): PostList = copy(
    posts = posts.map { post ->
        if (post.postId == postId) post.copy(crewRun = post.crewRun?.copy(myAnswer = answer)) else post
    }
)

// ---------- 크루 관리(크루장) ----------

/** 확인을 받는 관리 행동. */
sealed interface ManageAction {
    data class Kick(val member: CrewMembers.Member) : ManageAction

    data class Transfer(val member: CrewMembers.Member) : ManageAction

    data object Delete : ManageAction
}

data class CrewManageUiState(
    val crewId: Long = 0,
    val crewName: String = "",
    /** 크루 정보(GET /crews/{id}). 정보 수정 칸과 초대 코드를 채운다. */
    val detail: CrewDetail? = null,
    val isLoading: Boolean = true,
    val requests: PendingRequests? = null,
    val members: CrewMembers? = null,
    val inviteCode: String? = null,
    val form: CrewFormState = CrewFormState(),
    val confirming: ManageAction? = null,
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)

sealed interface CrewManageEvent {
    /** 크루를 지웠다: 크루 탭으로. */
    data object Deleted : CrewManageEvent
}

/**
 * 크루 관리(FR-113~FR-116, FR-123, FR-127, FR-136). 권한은 서버가 검사한다(FR-128): 크루장이 아니면 거부 문구를 보인다.
 * 정보 수정 칸과 초대 코드는 GET /crews/{id}(크루장에게는 초대 코드가 함께 온다)로 채운다.
 */
@HiltViewModel
class CrewManageViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewManageUiState())
    val state: StateFlow<CrewManageUiState> = _state.asStateFlow()

    private val _events = Channel<CrewManageEvent>(Channel.BUFFERED)
    val events: Flow<CrewManageEvent> = _events.receiveAsFlow()
    private var bound: Long? = null

    fun bind(key: Routes.CrewManage) {
        if (bound == key.crewId) return
        bound = key.crewId
        _state.value = CrewManageUiState(crewId = key.crewId)
        load()
    }

    /** 크루 정보·가입 신청·크루원을 읽는다. 정보 수정 칸은 처음 읽을 때만 채운다(쓰던 값을 덮지 않는다). */
    fun load() {
        val crewId = _state.value.crewId
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val detail = crews.detail(crewId)
            val requests = crews.pendingRequests(crewId)
            val members = crews.members(crewId)
            _state.update { st ->
                val info = (detail as? Outcome.Success)?.value
                st.copy(
                    isLoading = false,
                    detail = info ?: st.detail,
                    crewName = info?.name ?: st.crewName,
                    form = if (st.detail == null && info != null) info.toFormState() else st.form,
                    inviteCode = st.inviteCode ?: info?.inviteCode,
                    requests = (requests as? Outcome.Success)?.value,
                    members = (members as? Outcome.Success)?.value,
                    error = listOf(detail, requests, members)
                        .firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }
                        ?.let(CrewErrorText::of)
                )
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onApprove(request: PendingRequests.Request) = decide(request, shouldApprove = true)

    fun onReject(request: PendingRequests.Request) = decide(request, shouldApprove = false)

    private fun decide(request: PendingRequests.Request, shouldApprove: Boolean) = act {
        val r = if (shouldApprove) crews.approveJoin(request.requestId) else crews.rejectJoin(request.requestId)
        r.done(
            if (shouldApprove) {
                uiText(R.string.crew_manage_approved, request.displayName)
            } else {
                uiText(R.string.crew_manage_rejected, request.displayName)
            }
        )
    }

    /** 초대 코드 재발급(FR-114): 이전 코드는 바로 무효, 새 코드는 7일 유효. */
    fun onReissue() = act {
        when (val r = crews.reissueInviteCode(_state.value.crewId)) {
            is Outcome.Success -> {
                _state.update { it.copy(inviteCode = r.value) }
                uiText(R.string.crew_manage_invite_reissued)
            }

            is Outcome.Failure -> CrewErrorText.of(r.error)
        }
    }

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form) }

    /** 정보 수정(FR-113). */
    fun onSaveInfo() {
        val form = _state.value.form
        if (!form.isValid) return
        act {
            val r = crews.changeInfo(_state.value.crewId, form.toForm())
            if (r is Outcome.Success) _state.update { s -> s.copy(crewName = form.name.trim()) }
            r.done(uiText(R.string.crew_manage_info_changed))
        }
    }

    fun onAsk(action: ManageAction) = _state.update { it.copy(confirming = action) }

    fun onDismiss() = _state.update { it.copy(confirming = null) }

    fun onConfirm() {
        val action = _state.value.confirming ?: return
        val crewId = _state.value.crewId
        _state.update { it.copy(confirming = null) }
        act {
            when (action) {
                is ManageAction.Kick -> crews.kick(
                    crewId,
                    action.member.memberId
                ).done(uiText(R.string.crew_manage_kicked, action.member.displayName))

                is ManageAction.Transfer ->
                    crews.transferLeadership(
                        crewId,
                        action.member.memberId
                    ).done(uiText(R.string.crew_manage_transferred, action.member.displayName))

                ManageAction.Delete -> when (val r = crews.delete(crewId)) {
                    is Outcome.Success -> {
                        deleted = true
                        _events.trySend(CrewManageEvent.Deleted)
                        null
                    }

                    is Outcome.Failure -> CrewErrorText.of(r.error)
                }
            }
        }
    }

    /** 처리 한 번: [block]이 돌려준 문구를 안내하고 목록을 다시 읽는다. */
    private var deleted = false

    private fun act(block: suspend () -> UiText?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val message = block()
            _state.update { it.copy(isBusy = false, message = message) }
            // 지운 크루는 다시 읽지 않는다.
            if (!deleted) load()
        }
    }

    private fun Outcome<Unit>.done(success: UiText): UiText = when (this) {
        is Outcome.Success -> success
        is Outcome.Failure -> CrewErrorText.of(error)
    }
}

/** 크루 정보 → 정보 수정 칸. */
internal fun CrewDetail.toFormState(): CrewFormState =
    CrewFormState(name = name, intro = intro.orEmpty(), tags = tags, visibility = visibility, joinPolicy = joinPolicy)
