package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
import timber.log.Timber

/**
 * 내 크루 홈(FR-125, FR-133, FR-135, FR-137~FR-141, FR-144, FR-145, FR-213, 피그마 F-05·F-11·F-17·F-18·F-22).
 * 크루 이름·인원·내 역할·초대 코드는 detail(GET /crews/{id})로 읽고, 탭을 처음 열 때 그 탭의 포트만 부른다(레이스 race +
 * crewRanking, 크루런 posts(CREW_RUN)). 크루원 쪽(⋯ 메뉴)을 열면 members를 읽는다. 게시판·공지·투표·앨범·대면 모임은 앱에서
 * 뺐다(S15P31A207-236, 서버 API는 남아 있다).
 * 크루런은 끝까지 내리면 다음 쪽(nextCursor)을 이어 받는다. 크루런 모집글의 대기실 단추는 서버 시각 추정(ServerClock)으로
 * 열림 30분 전·모집 일시를 따져 열고 닫는다(FR-133·FR-147). 버튼 노출은 detail.myRole(3.0.3)로 정하고 권한은 서버가 다시 검사한다.
 * 공동 목표는 주간 공동 목표 화면(M8, CrewGoalSetViewModel)에서 정하고, 달성한 주의 축하 카드는 ui.share 방식으로 공유한다(FR-141).
 */
@HiltViewModel
class CrewHomeViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val serverClock: ServerClock,
    private val celebration: CelebrationSharer
) : ViewModel() {
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
    private var periodJob: Job? = null

    /** 크루 홈을 연다. [initialTab]은 알림으로 들어올 때 먼저 보일 탭(크루런, D-150)이다. */
    fun bind(key: Routes.CrewHome, initialTab: CrewTab = CrewTab.RACE) {
        if (bound == key.crewId) return
        bound = key.crewId
        _state.value = CrewHomeUiState(crewId = key.crewId, tab = initialTab)
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
            CrewTab.SCHEDULE -> s.crewRuns != null
        }
        if (!loaded) refresh()
    }

    /** ⋯ 메뉴의 쪽(크루 정보·크루원)을 열거나 홈으로 돌아간다. 크루원 쪽은 처음 열 때 읽는다. */
    fun onPage(page: CrewHomePage) {
        _state.update { it.copy(page = page, error = null) }
        if (page == CrewHomePage.MEMBERS && _state.value.members == null) refresh()
    }

    /** 지금 보이는 것을 다시 읽는다: 크루원 쪽이면 크루원, 아니면 지금 탭. */
    fun refresh() {
        val s = _state.value
        refreshJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        refreshJob = viewModelScope.launch {
            val error = when {
                s.page == CrewHomePage.MEMBERS -> loadMembers(s)

                s.tab == CrewTab.RACE -> loadRace(s)

                // 크루런 탭은 크루런 일정만 보인다(피그마 F-11).
                else -> {
                    _state.update { it.copy(scheduleNow = serverClock.now()) }
                    loadPosts(s.crewId, PostList.Kind.CREW_RUN)
                }
            }
            _state.update { it.copy(isLoading = false, error = error?.let(CrewErrorText::of)) }
        }
    }

    /** 레이스 탭: 레이스와 크루 랭킹. */
    private suspend fun loadRace(s: CrewHomeUiState): AppError? {
        val race = crews.race(s.crewId, null)
        val ranking = crews.crewRanking(s.crewId, s.period)
        _state.update {
            it.copy(
                race = (race as? Outcome.Success)?.value ?: it.race,
                ranking = (ranking as? Outcome.Success)?.value ?: it.ranking
            )
        }
        return (race as? Outcome.Failure)?.error ?: (ranking as? Outcome.Failure)?.error
    }

    /** 크루원 목록. 줄마다 이번 주 거리를 보이므로 레이스를 아직 읽지 않았으면 함께 읽는다(와이어 E4). */
    private suspend fun loadMembers(s: CrewHomeUiState): AppError? {
        val members = crews.members(s.crewId)
        if (members is Outcome.Success) _state.update { it.copy(members = members.value) }
        if (_state.value.race == null) {
            val race = crews.race(s.crewId, null)
            if (race is Outcome.Success) _state.update { it.copy(race = race.value) }
        }
        return (members as? Outcome.Failure)?.error
    }

    private suspend fun loadPosts(crewId: Long, kind: PostList.Kind): AppError? =
        when (val r = crews.posts(crewId, kind)) {
            is Outcome.Success -> {
                _state.update { it.copy(crewRuns = r.value) }
                null
            }

            is Outcome.Failure -> r.error
        }

    fun onPeriod(period: CrewRanking.Period) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        val crewId = _state.value.crewId
        // 연타하면 앞 기간 요청을 취소한다(늦게 온 이전 기간 순위가 지금 기간을 덮지 않게).
        periodJob?.cancel()
        periodJob = viewModelScope.launch {
            when (val r = crews.crewRanking(crewId, period)) {
                is Outcome.Success -> _state.update { it.copy(ranking = r.value) }
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** 같은 화면의 다른 ViewModel(신고)이 낸 안내를 이 화면의 안내 줄로 보인다. */
    fun onMessage(text: UiText) = _state.update { it.copy(message = text) }

    // 축하 카드(FR-141)

    /** 축하 카드 공유(FR-141, TC-7496). */
    fun onShareCelebration(card: CelebrationCard) {
        if (_state.value.isSharing) return
        _state.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            val message = try {
                celebration.share(card)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "축하 카드 공유 실패")
                UiText.Res(R.string.crew_celebration_failed)
            } finally {
                _state.update { it.copy(isSharing = false) }
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    // 일정 글 삭제

    /** 지울 수 있는 글(canDelete: 본인 글, 크루장·운영진은 모든 글, FR-213)만 확인을 받는다(되돌릴 수 없다). */
    fun onDeleteAsk(item: PostList.Item) {
        if (item.canDelete) _state.update { it.copy(confirmDelete = item) }
    }

    fun onDeleteDismiss() = _state.update { it.copy(confirmDelete = null) }

    fun onDeleteConfirm() {
        val item = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        onDeletePost(item)
    }

    /** 글 삭제(FR-130·FR-134·FR-213). 크루장·운영진이 남의 글을 지우면 서버가 감사기록에 남긴다. */
    fun onDeletePost(item: PostList.Item) {
        if (!item.canDelete || _state.value.isBusy) return
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

    // 크루런 일정

    /**
     * 크루런 참석 응답(FR-145, D-99): 참석·불참·미정. 응답 기한 전에는 바꿀 수 있다. 보낸 응답을 바로 그 글의 내 응답으로 보이고, 서버
     * 목록을 다시 읽어 응답 수·내 응답을 맞춘다.
     */
    fun onAttendance(item: PostList.Item, answer: AttendanceAnswer) {
        viewModelScope.launch {
            when (val r = crews.answerAttendance(item.postId, answer)) {
                is Outcome.Success -> {
                    _state.update { st ->
                        st.crewRuns?.let { st.copy(crewRuns = it.withAnswer(item.postId, answer)) } ?: st
                    }
                    reloadPosts(item.kind)
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /**
     * 참석 취소(참석 칩을 끔, 피그마 F-11·F-13b, W6): 응답을 거둔다(DELETE /posts/{id}/attendance). 바로 그 글의 내 응답을 비우고 서버 목록을
     * 다시 읽어 참석 수를 맞춘다.
     */
    fun onAttendanceWithdraw(item: PostList.Item) {
        viewModelScope.launch {
            when (val r = crews.withdrawAttendance(item.postId)) {
                is Outcome.Success -> {
                    _state.update { st ->
                        st.crewRuns?.let { st.copy(crewRuns = it.withAnswer(item.postId, null)) } ?: st
                    }
                    reloadPosts(item.kind)
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 크루런 탭의 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun onLoadMore() {
        val s = _state.value
        if (s.tab != CrewTab.SCHEDULE || s.page != CrewHomePage.HOME) return
        val kind = PostList.Kind.CREW_RUN
        val cursor = s.crewRuns?.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val r = crews.posts(s.crewId, kind, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val current = st.crewRuns ?: r.value
                    val known = current.posts.map { it.postId }.toSet()
                    val merged =
                        PostList(current.posts + r.value.posts.filterNot { it.postId in known }, r.value.nextCursor)
                    st.copy(crewRuns = merged).copy(isLoadingMore = false)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error))
                }
            }
        }
    }

    // 크루원·탈퇴·내 소개

    /** 크루원이 남은 크루장은 탈퇴 대신 양도 안내를 본다(와이어 E7, FR-126). 혼자 남은 크루장은 확인 뒤 서버가 삭제를 안내한다. */
    fun onLeaveAsk() = _state.update {
        if (it.isLeader &&
            (it.headcount ?: 0) > 1
        ) {
            it.copy(isLeaveBlocked = true)
        } else {
            it.copy(isConfirmingLeave = true)
        }
    }

    fun onLeaveDismiss() = _state.update { it.copy(isConfirmingLeave = false) }

    fun onLeaveBlockedDismiss() = _state.update { it.copy(isLeaveBlocked = false) }

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

    /** 크루 내 소개 편집(와이어 E6): 지금 내 소개로 채워 연다. */
    fun onIntroEdit() = _state.update { s ->
        val mine = s.members?.members?.firstOrNull { it.isMe }?.crewIntro.orEmpty()
        s.copy(isEditingIntro = true, introDraft = mine.take(CrewHomeUiState.INTRO_MAX))
    }

    fun onIntroDraft(text: String) = _state.update { it.copy(introDraft = text.take(CrewHomeUiState.INTRO_MAX)) }

    fun onIntroDismiss() = _state.update { it.copy(isEditingIntro = false) }

    /** 크루 내 한줄 소개(FR-144, 40자). 러너 성향 태그는 크루 안에서도 고르지 않는다(CR-04). */
    fun onIntroSave() {
        val s = _state.value
        _state.update { it.copy(isEditingIntro = false, isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.editCrewProfile(s.crewId, s.introDraft.trim())) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(isBusy = false, message = uiText(R.string.crew_home_intro_changed), members = null)
                    }
                    if (_state.value.page == CrewHomePage.MEMBERS) refresh()
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    // 초대 코드(피그마 F-18b·F-25b)

    fun onInviteOpen() = _state.update { it.copy(isInviteOpen = true) }

    fun onInviteClose() = _state.update { it.copy(isInviteOpen = false) }

    fun onInviteCopied() = _state.update {
        it.copy(isInviteOpen = false, message = uiText(R.string.crew_manage_invite_copied))
    }

    /**
     * 모집글마다 대기실 단추 상태를 정하고, 시각이 지나 바뀔 수 있는 글(열리기 전·열림)이 남아 있는 동안 1초마다 다시 정한다.
     * 목록을 다시 받으면 collectLatest가 이 반복을 새로 시작한다. 강조 카드의 남은 시간을 위해 지금 서버 시각(scheduleNow)도 함께 갱신한다.
     */
    private suspend fun watchLobbies(runs: PostList?) {
        val infos = runs?.posts.orEmpty().mapNotNull { post -> post.crewRun?.let { post.postId to it } }
        while (true) {
            val now = serverClock.now()
            val entries = infos.associate { (postId, info) -> postId to LobbyAvailability.of(info, now) }
            // 강조 카드의 남은 시간(F-11)도 같은 서버 시각으로 1초마다 센다.
            _state.update { it.copy(lobbyEntries = entries, scheduleNow = now) }
            if (entries.values.all { it == LobbyAvailability.Closed }) return
            delay(LOBBY_TICK_MILLIS)
        }
    }

    /** 응답·삭제·글쓰기 뒤 갱신. 이어 받아 둔 범위까지 다시 읽는다(postsCovering). */
    private suspend fun reloadPosts(kind: PostList.Kind) {
        moreJob?.cancel()
        _state.update { it.copy(isLoadingMore = false) }
        val loaded = _state.value.crewRuns?.posts?.size ?: 0
        when (val r = crews.postsCovering(_state.value.crewId, kind, loaded)) {
            is Outcome.Success -> _state.update { it.copy(crewRuns = r.value) }
            is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
        }
    }

    private companion object {
        const val LOBBY_TICK_MILLIS = 1_000L
    }
}

/** [postId] 크루런의 내 응답을 [answer]로 바꾼 목록. */
private fun PostList.withAnswer(postId: Long, answer: AttendanceAnswer?): PostList = copy(
    posts = posts.map { post ->
        if (post.postId != postId) post else post.copy(crewRun = post.crewRun?.copy(myAnswer = answer))
    }
)
