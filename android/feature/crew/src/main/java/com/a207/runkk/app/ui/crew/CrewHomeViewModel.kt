package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.CrewRunReturnReceipt
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewCelebrationStore
import com.a207.runkk.app.domain.crew.CrewChange
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewReadSession
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunSignals
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.MyRunState
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.ui.share.KakaoShareOutcome
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
import kotlinx.coroutines.flow.combine
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
 *
 * 실시간 반영(설계 05-32): 크루런 탭은 진행 중인 크루런만 날짜순(order=SCHEDULED_ASC, activeOnly)으로 읽는다. 화면이 보이는 동안 이
 * 크루의 목록 변경 신호(CrewRunChanged SCHEDULE)를 받으면 크루런 탭이 열려 있을 때만 읽은 범위를 다시 읽는다(CrewReadSession으로
 * 합친다, 「새로」 표식 없음). 탭을 다시 고르면 그 탭을 늘 다시 읽고(레이스 재진입, TC-11017), 당겨서 새로고침은 지금 값을 둔 채 읽는다.
 * 레이스는 결과·기여 신호로 스스로 다시 읽지 않는다([임시:FR-성-370]). 레이스를 새로 읽으면 직전 성공 조회보다 늘어난 확정 거리를
 * 2초 강조하고, 직전 값이 없을 때는 결과 복귀 값이 있으면 목표 카드와 내 줄만 강조한다(증가량은 추정하지 않는다, [임시:D-성-889]).
 */
@HiltViewModel
class CrewHomeViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val serverClock: ServerClock,
    private val celebration: CelebrationSharer,
    private val visibility: ProfileVisibilitySaver,
    /** 축하 팝업을 그 주에 본 기록(D-655). 시험은 기본 메모리 구현을 쓴다. */
    private val celebrationStore: CrewCelebrationStore = CrewCelebrationStore.inMemory(),
    /** 크루런 변경 신호(설계 05-32). 시험에서 없으면 신호를 받지 않는다. */
    private val signals: CrewRunSignals? = null
) : ViewModel() {
    private val _state = MutableStateFlow(CrewHomeUiState())
    val state: StateFlow<CrewHomeUiState> = _state.asStateFlow()

    /** 화면이 RESUMED인가(LifecycleResumeEffect가 알린다). 시험처럼 알리지 않으면 RESUMED로 본다. */
    private val isResumed = MutableStateFlow(true)

    init {
        // 대기실 단추·남은 시간 1초 갱신은 크루런 탭이 보이고(홈 쪽) 화면이 RESUMED일 때만 돈다(리뷰 M3). 그 밖에는 한 번만 정한다.
        viewModelScope.launch {
            combine(
                _state.map { it.crewRuns }.distinctUntilChanged(),
                _state.map { it.tab == CrewTab.SCHEDULE && it.page == CrewHomePage.HOME }.distinctUntilChanged(),
                isResumed
            ) { runs, isScheduleShown, resumed -> runs to (isScheduleShown && resumed) }
                .distinctUntilChanged()
                .collectLatest { (runs, isTicking) -> watchLobbies(runs, isTicking) }
        }
        // 달리는 크루런 강조 카드(피그마 F-11e·f·g): 출발 시각과 내 거리를 결과에서 한 번 읽는다(내 상태가 바뀌면 다시).
        viewModelScope.launch {
            _state.map { st ->
                upcomingSchedules(st).firstOrNull()?.takeIf { it.crewRun?.status == CrewRunStatus.RUNNING }
            }
                .map { hero -> hero?.let { it.postId to it.crewRun?.myRun } }
                .distinctUntilChanged()
                .collectLatest { key -> key?.let { (postId, myRun) -> readHeroResult(postId, myRun) } }
        }
    }

    /** 달리는 강조 카드의 크루런 결과를 읽어 담는다. 실패하면 모집 일시 기준 시간·숫자 없는 「완주했어요」로 그린다. */
    private suspend fun readHeroResult(postId: Long, myRun: MyRunState?) {
        repeat(HERO_READS) {
            val result = (crews.crewRunResult(postId) as? Outcome.Success)?.value ?: return
            _state.update { it.copy(heroResults = it.heroResults + (postId to result)) }
            // 끝냈는데 결과에 내 기록이 아직 없으면(업로드 직전) 잠시 뒤 다시 읽는다. 키가 바뀌면 collectLatest가 끊는다
            val isSettled = myRun != MyRunState.ENDED || result.participants.any { it.isMe && it.hasEnded }
            if (isSettled) return
            delay(HERO_REREAD_MILLIS)
        }
    }

    /** 크루런 탭 목록 다시 읽기(신호를 합친다). */
    private val scheduleReads = CrewReadSession(viewModelScope) { reloadPosts() }

    /** 결과 「확인」 뒤 아직 레이스에 쓰지 않은 복귀 값(직전 값이 없을 때의 강조 근거). */
    private var pendingReturn: CrewRunReturnReceipt? = null
    private var highlightJob: Job? = null

    init {
        // 화면이 보이는 동안만 변경 신호를 받는다(임대 반납은 수집 취소가 한다).
        viewModelScope.launch {
            isResumed.collectLatest { resumed ->
                if (resumed) signals?.changes()?.collect(::onChange)
            }
        }
    }

    /** 이 크루의 목록 변경이면 크루런 탭이 보일 때만 다시 읽는다. 레이스·결과 신호로는 읽지 않는다. */
    private fun onChange(change: CrewChange) {
        val s = _state.value
        val isScheduleShown = s.tab == CrewTab.SCHEDULE && s.page == CrewHomePage.HOME
        if (change.crewId == s.crewId && change.resource == CrewChange.Resource.SCHEDULE && isScheduleShown) {
            scheduleReads.refresh()
        }
    }

    private val _events = Channel<CrewHomeEvent>(Channel.BUFFERED)
    val events: Flow<CrewHomeEvent> = _events.receiveAsFlow()

    private var bound: Long? = null

    /** 이미 받았거나 받는 중인 크루원 얼굴 키(키마다 한 번 받는다). 실패한 키는 빼서 다음 갱신에 다시 받는다. */
    private val requestedFaces = mutableSetOf<String>()

    /** 크루 로고 바이트(크루 홈 머리·크루 정보·정보 수정의 크루 이미지가 읽는다, DV-8). */
    suspend fun logo(url: String): Outcome<ByteArray> = crews.logo(url)
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var moreJob: Job? = null
    private var periodJob: Job? = null
    private var logoJob: Job? = null

    /** 크루 홈을 연다. [initialTab]은 알림으로 들어올 때 먼저 보일 탭(크루런, D-150)이고, 경로의 page가 있으면 그 쪽을 먼저 연다. */
    fun bind(key: Routes.CrewHome, initialTab: CrewTab = CrewTab.RACE) {
        if (bound == key.crewId) return
        bound = key.crewId
        requestedFaces.clear()
        _state.value = CrewHomeUiState(
            crewId = key.crewId,
            tab = CrewTab.of(key.tab, initialTab),
            page = CrewHomePage.of(key.page)
        )
        loadDetail()
        refresh()
    }

    private var resumedOnce = false

    /**
     * 화면에 돌아올 때(관리·모집·결과에서 복귀) 크루 정보와 지금 탭을 다시 읽는다. 처음 표시는 bind가 읽는다. [receipt]는 크루런 결과
     * 「확인」의 복귀 값이다(레이스 강조 근거, 설계 05-32 [임시:D-성-888]).
     */
    fun onResumed(receipt: CrewRunReturnReceipt? = null) {
        receipt?.let { pendingReturn = it }
        isResumed.value = true
        if (resumedOnce && bound != null) {
            loadDetail()
            refresh()
        }
        resumedOnce = true
    }

    /** 화면이 멈췄다(ON_PAUSE·떠남). 크루런 1초 갱신을 멈춘다. */
    fun onPaused() {
        isResumed.value = false
    }

    /** 크루 정보(이름·인원·내 역할). 실패하면 이름 칸을 비워 두고 안내한다(탭 내용은 따로 읽는다). */
    private fun loadDetail() {
        val crewId = _state.value.crewId
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            when (val r = crews.detail(crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(detail = r.value) }
                    if (_state.value.page == CrewHomePage.INFO) loadRequests()
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /**
     * 탭을 옮긴다. 옮길 때마다 크루 정보(역할)를 다시 읽어, 그 사이 크루장이 되면 바로 「목표 정하기」를 보인다(DV-성 9(b)). 레이스·
     * 크루런 탭은 고를 때마다 다시 읽는다(재진입 읽기, 설계 05-32 [임시:FR-성-372], TC-11017). 이미 읽은 값은 읽는 동안 그대로 보인다.
     */
    fun onTab(tab: CrewTab) {
        val isChanged = tab != _state.value.tab
        if (isChanged) loadDetail()
        _state.update { it.copy(tab = tab) }
        // 채팅 탭은 ChatTabContent가 방을 열어 스스로 읽는다(D-653).
        if (tab != CrewTab.CHAT && (isChanged || isTabEmpty(tab))) refresh()
    }

    private fun isTabEmpty(tab: CrewTab): Boolean = when (tab) {
        CrewTab.RACE -> _state.value.race == null
        CrewTab.SCHEDULE -> _state.value.crewRuns == null
        CrewTab.CHAT -> false
    }

    /**
     * 당겨서 새로고침(설계 05-32 [임시:FR-성-366], TC-11034): 지금 쪽·탭을 다시 읽는다. 실패하면 지금 값을 그대로 두고 안내 줄로
     * 알린다(읽은 값이 없을 때만 오류 화면).
     */
    fun onPullRefresh() {
        val s = _state.value
        if (s.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val error = when {
                s.page == CrewHomePage.MEMBERS -> loadMembers(s)

                s.tab == CrewTab.RACE -> loadRace(s)

                s.tab == CrewTab.CHAT -> null

                else -> {
                    _state.update { it.copy(scheduleNow = serverClock.now()) }
                    readCovering()
                }
            }
            _state.update { st ->
                val hasValue = when {
                    s.page == CrewHomePage.MEMBERS -> st.members != null
                    s.tab == CrewTab.RACE -> st.race != null
                    else -> st.crewRuns != null
                }
                val text = error?.let(CrewErrorText::of)
                st.copy(
                    isRefreshing = false,
                    message = if (hasValue && text != null) text else st.message,
                    error = if (!hasValue) text else null
                )
            }
        }
    }

    /**
     * ⋯ 메뉴의 쪽(크루 정보·크루원)이나 크루 정보 수정(F-26)을 열거나 홈으로 돌아간다. 크루원 쪽은 처음 열 때 읽고, 크루 정보는 열 때마다
     * 크루 정보(인원·역할, DV-성 9(d))와 크루장이면 가입 신청(F-25)을 다시 읽는다. 수정 쪽은 지금 크루 정보로 입력을 채우고 고른 로고를 비운다.
     */
    fun onPage(page: CrewHomePage) {
        _state.update { s ->
            val isEdit = page == CrewHomePage.EDIT
            val form = if (isEdit) s.detail?.toFormState() ?: s.editForm else s.editForm
            s.copy(
                page = page,
                error = null,
                isMenuOpen = false,
                editForm = form,
                editLogo = if (isEdit) null else s.editLogo
            )
        }
        if (page == CrewHomePage.MEMBERS && _state.value.members == null) refresh()
        if (page == CrewHomePage.INFO) loadDetail()
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

                s.tab == CrewTab.CHAT -> null

                // 크루런 탭은 진행 중인 크루런 일정만 날짜순으로 보인다(피그마 F-11, 설계 05-32 4.5).
                else -> {
                    _state.update { it.copy(scheduleNow = serverClock.now()) }
                    if (_state.value.crewRuns == null) loadPosts(s.crewId) else readCovering()
                }
            }
            // 이미 읽은 값이 있으면 그대로 두고 안내 줄로 알린다(탭 재진입·복귀 읽기 실패, 설계 05-32 TC-11034).
            _state.update { st ->
                val hasValue = when {
                    s.page == CrewHomePage.MEMBERS -> st.members != null
                    s.tab == CrewTab.RACE -> st.race != null
                    s.tab == CrewTab.SCHEDULE -> st.crewRuns != null
                    else -> false
                }
                val text = error?.let(CrewErrorText::of)
                if (hasValue && text != null) {
                    st.copy(isLoading = false, message = text)
                } else {
                    st.copy(isLoading = false, error = text)
                }
            }
        }
    }

    /** 레이스 탭: 레이스와 크루 랭킹. 새로 읽은 레이스가 직전 성공 조회보다 늘었으면 2초 강조한다(설계 05-32 [임시:D-성-889]). */
    private suspend fun loadRace(s: CrewHomeUiState): AppError? {
        val race = crews.race(s.crewId, null)
        val ranking = crews.crewRanking(s.crewId, s.period)
        val latest = (race as? Outcome.Success)?.value
        val highlight = latest?.let { raceHighlightOf(_state.value.race, it, hasReturn = pendingReturn != null) }
        if (latest != null) pendingReturn = null
        _state.update {
            it.copy(
                race = latest ?: it.race,
                ranking = (ranking as? Outcome.Success)?.value ?: it.ranking
            )
        }
        highlight?.let(::showHighlight)
        latest?.let { celebrateOnce(s.crewId, it) }
        return (race as? Outcome.Failure)?.error ?: (ranking as? Outcome.Failure)?.error
    }

    /** 늘어난 거리를 처음 그린 때부터 2초 보이고 지운다(TC-11037). 새 강조가 오면 앞 강조를 바꾼다. */
    private fun showHighlight(highlight: RaceHighlight) {
        highlightJob?.cancel()
        _state.update { it.copy(raceHighlight = highlight) }
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_MILLIS)
            _state.update { it.copy(raceHighlight = null) }
        }
    }

    /** 이번 주 공동 목표를 달성했고 이 기기에서 그 주에 아직 팝업을 보지 않았으면 한 번 띄운다(피그마 F-08b, D-655). */
    private fun celebrateOnce(crewId: Long, race: RaceStatus) {
        if (race.achievedAt == null || race.celebration == null) return
        val weekStart = raceWeekStart(race)
        if (celebrationStore.isSeen(crewId, weekStart)) return
        celebrationStore.markSeen(crewId, weekStart)
        _state.update { it.copy(celebration = race) }
    }

    /** 달성 띠의 「자세히 보기」(피그마 F-08). */
    fun onCelebrationOpen() = _state.update { s -> s.copy(celebration = s.race?.takeIf { it.celebration != null }) }

    fun onCelebrationClose() = _state.update { it.copy(celebration = null) }

    /** 크루원 목록. 줄마다 서버가 준 이 크루 누적 거리를 보인다(피그마 F-22, contract-W7 2장). */
    private suspend fun loadMembers(s: CrewHomeUiState): AppError? {
        val members = crews.members(s.crewId)
        if (members is Outcome.Success) {
            _state.update { it.copy(members = members.value) }
            readMemberFaces(members.value)
        }
        return (members as? Outcome.Failure)?.error
    }

    /**
     * 크루원의 셀카 얼굴 부위 그림을 얼굴 키마다 한 번 받는다([임시:D-성-236], 크루런 대기실 readLobbyFaces와 같은 방식). 받아 둔
     * 그림(캐시)은 목록과 함께 바로 그리고, 없는 것만 받는다. 받지 못하면 오류를 띄우지 않고 기본 얼굴로 두며 다음 목록 갱신에 다시
     * 받는다([임시:FR-성-24]).
     */
    private fun readMemberFaces(members: CrewMembers) {
        val keys = members.members.mapNotNull { it.faceKey }.distinct().filter { requestedFaces.add(it) }
        val cached = keys.mapNotNull { key ->
            crews.cachedFaceLayers(key)?.takeIf { it.isNotEmpty() }?.let { key to it }
        }
        if (cached.isNotEmpty()) _state.update { it.copy(memberFaces = it.memberFaces + cached) }
        keys.filter { key -> cached.none { it.first == key } }.forEach { key ->
            viewModelScope.launch {
                when (val r = crews.faceLayers(key)) {
                    is Outcome.Success -> r.value.takeIf { it.isNotEmpty() }?.let { layers ->
                        _state.update { it.copy(memberFaces = it.memberFaces + (key to layers)) }
                    }

                    is Outcome.Failure -> requestedFaces.remove(key)
                }
            }
        }
    }

    private suspend fun loadPosts(crewId: Long): AppError? = when (val r = crews.activeCrewRuns(crewId)) {
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
                    reloadPosts()
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
                    reloadPosts()
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
                    reloadPosts()
                }

                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 크루런 탭의 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun onLoadMore() {
        val s = _state.value
        if (s.tab != CrewTab.SCHEDULE || s.page != CrewHomePage.HOME) return
        val cursor = s.crewRuns?.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            when (val r = crews.activeCrewRuns(s.crewId, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val current = st.crewRuns ?: r.value
                    val known = current.posts.map { it.postId }.toSet()
                    val merged =
                        PostList(current.posts + r.value.posts.filterNot { it.postId in known }, r.value.nextCursor)
                    st.copy(crewRuns = merged).copy(isLoadingMore = false)
                }

                // 기준 글이 사라져 커서를 이어 읽지 못하면(400) 첫 쪽부터 읽은 범위를 다시 읽는다(설계 05-32 4.5).
                is Outcome.Failure -> if (r.error == AppError.Rejected(BAD_REQUEST)) {
                    _state.update { it.copy(isLoadingMore = false) }
                    reloadPosts()
                } else {
                    _state.update { it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error)) }
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
                is Outcome.Success -> leftCrew()
                is Outcome.Failure -> _state.update { it.copy(isBusy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 내 정보 시트(피그마 F-23): 지금 내 소개와 계정의 나이·성별 공개 설정으로 채워 연다. */
    fun onIntroEdit() = _state.update { s ->
        val me = s.me
        s.copy(
            isEditingIntro = true,
            introDraft = me?.crewIntro.orEmpty().take(CrewHomeUiState.INTRO_MAX),
            isAgePublic = me?.isAgePublic ?: false,
            isGenderPublic = me?.isGenderPublic ?: false
        )
    }

    fun onAgePublic(isPublic: Boolean) = _state.update { it.copy(isAgePublic = isPublic) }

    fun onGenderPublic(isPublic: Boolean) = _state.update { it.copy(isGenderPublic = isPublic) }

    fun onIntroDraft(text: String) = _state.update { it.copy(introDraft = text.take(CrewHomeUiState.INTRO_MAX)) }

    fun onIntroDismiss() = _state.update { it.copy(isEditingIntro = false) }

    /**
     * 내 정보 저장(FR-144, 피그마 F-23): 소개가 바뀌었으면 PATCH /crews/{id}/me, 공개 토글이 바뀌었으면 계정 공개 설정(PATCH
     * /me/profile)을 저장한다. 둘 다 되면 시트를 닫고 크루원을 다시 읽는다. 하나라도 실패하면 시트를 연 채 안내한다.
     */
    fun onIntroSave() {
        val s = _state.value
        if (s.isBusy) return
        val me = s.me
        val intro = s.introDraft.trim()
        val introChanged = intro != me?.crewIntro.orEmpty()
        // 내 공개 설정을 모르면(옛 서버) 토글을 숨기고 저장하지 않는다(건드리지 않은 쪽을 PRIVATE로 덮지 않게).
        val knowsVisibility = me?.isAgePublic != null && me.isGenderPublic != null
        val visibilityChanged = knowsVisibility &&
            (s.isAgePublic != me?.isAgePublic || s.isGenderPublic != me?.isGenderPublic)
        if (!introChanged && !visibilityChanged) {
            _state.update { it.copy(isEditingIntro = false) }
            return
        }
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val introError = if (introChanged) {
                (
                    crews.editCrewProfile(
                        s.crewId,
                        intro
                    ) as? Outcome.Failure
                    )?.error
            } else {
                null
            }
            val error = introError ?: if (visibilityChanged) {
                (visibility.save(s.isAgePublic, s.isGenderPublic) as? Outcome.Failure)?.error
            } else {
                null
            }
            if (error != null) {
                _state.update { it.copy(isBusy = false, message = CrewErrorText.of(error)) }
                if (introChanged && introError == null) reloadMembers()
                return@launch
            }
            _state.update {
                it.copy(isBusy = false, isEditingIntro = false, message = uiText(R.string.crew_home_intro_changed))
            }
            reloadMembers()
        }
    }

    // 초대 코드(피그마 F-18b·F-25b)

    fun onInviteOpen() = _state.update { it.copy(isInviteOpen = true) }

    fun onInviteClose() = _state.update { it.copy(isInviteOpen = false) }

    fun onInviteCopied() = _state.update {
        it.copy(isInviteOpen = false, message = uiText(R.string.crew_manage_invite_copied))
    }

    /** 초대 링크를 복사했다: 창을 닫고 안내한다(코드 복사와 같다). */
    fun onInviteLinkCopied() = _state.update {
        it.copy(isInviteOpen = false, message = uiText(R.string.crew_invite_link_copied))
    }

    /**
     * 「카카오톡으로 보내기」의 결과([임시:FR-성-37]). 열지 못했으면 안내 이벤트를 보내고 창은 그대로 둔다(다른 방법을 고를 수 있게).
     * 카카오톡이나 웹 공유를 열었으면 창을 그대로 둔다(돌아와서 닫는다).
     */
    fun onInviteKakaoResult(outcome: KakaoShareOutcome) {
        if (outcome == KakaoShareOutcome.FAILED) _events.trySend(CrewHomeEvent.InviteKakaoFailed)
    }

    /** 「다른 앱으로 공유」의 공유 화면을 열지 못했다(공유할 앱 없음): 창은 그대로 두고 이벤트로 알린다. */
    fun onInviteShareFailed() {
        _events.trySend(CrewHomeEvent.InviteShareFailed)
    }

    // ⋯ 메뉴(피그마 F-17·F-24)

    fun onMenuOpen() = _state.update { it.copy(isMenuOpen = true) }

    fun onMenuClose() = _state.update { it.copy(isMenuOpen = false) }

    // 가입 신청(피그마 F-25, 크루장)

    /** 크루장이면 가입 신청을 읽는다. 실패하면 앞서 읽은 목록을 둔다. */
    private suspend fun loadRequests() {
        val s = _state.value
        if (!s.canManage) return
        when (val r = crews.pendingRequests(s.crewId)) {
            is Outcome.Success -> _state.update { it.copy(requests = r.value) }
            is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
        }
    }

    fun onApprove(request: PendingRequests.Request) = decide(request, shouldApprove = true)

    fun onReject(request: PendingRequests.Request) = decide(request, shouldApprove = false)

    private fun decide(request: PendingRequests.Request, shouldApprove: Boolean) = act {
        val r = if (shouldApprove) crews.approveJoin(request.requestId) else crews.rejectJoin(request.requestId)
        val text = if (shouldApprove) {
            uiText(R.string.crew_manage_approved, request.displayName)
        } else {
            uiText(R.string.crew_manage_rejected, request.displayName)
        }
        loadRequests()
        if (shouldApprove && r is Outcome.Success) reloadCrew()
        r.done(text)
    }

    // 크루원 정보 팝업(피그마 F-22b·F-29b)과 크루장 넘기기·내보내기(F-29c·F-29d)

    fun onMemberOpen(member: CrewMembers.Member) = _state.update { it.copy(openedMember = member) }

    fun onMemberClose() = _state.update { it.copy(openedMember = null) }

    /** 팝업을 닫고 넘기기 확인을 연다. 크루장이 다른 크루원에게만. */
    fun onTransferAsk(member: CrewMembers.Member) = _state.update {
        if (it.canManage && !member.isMe) it.copy(openedMember = null, confirmTransfer = member) else it
    }

    fun onTransferDismiss() = _state.update { it.copy(confirmTransfer = null) }

    fun onTransferConfirm() {
        if (_state.value.isBusy) return
        val member = _state.value.confirmTransfer ?: return
        _state.update { it.copy(confirmTransfer = null) }
        act {
            val r = crews.transferLeadership(_state.value.crewId, member.memberId)
            if (r is Outcome.Success) reloadCrew()
            r.done(uiText(R.string.crew_manage_transferred, member.displayName))
        }
    }

    fun onKickAsk(member: CrewMembers.Member) = _state.update {
        if (it.canManage && !member.isMe) it.copy(openedMember = null, confirmKick = member) else it
    }

    fun onKickDismiss() = _state.update { it.copy(confirmKick = null) }

    fun onKickConfirm() {
        if (_state.value.isBusy) return
        val member = _state.value.confirmKick ?: return
        _state.update { it.copy(confirmKick = null) }
        act {
            val r = crews.kick(_state.value.crewId, member.memberId)
            if (r is Outcome.Success) reloadCrew()
            r.done(uiText(R.string.crew_manage_kicked, member.displayName))
        }
    }

    // 크루 삭제(피그마 F-27)·크루 정보 수정(F-26), 크루장

    fun onCrewDeleteAsk() = _state.update { if (it.canManage) it.copy(isConfirmingCrewDelete = true) else it }

    fun onCrewDeleteDismiss() = _state.update { it.copy(isConfirmingCrewDelete = false) }

    /** 크루를 지운다(FR-116). 지우면 크루 탭(찾기)으로 간다. */
    fun onCrewDeleteConfirm() {
        if (!_state.value.isConfirmingCrewDelete || _state.value.isBusy) return
        _state.update { it.copy(isConfirmingCrewDelete = false) }
        act {
            when (val r = crews.delete(_state.value.crewId)) {
                is Outcome.Success -> {
                    leftCrew()
                    null
                }

                is Outcome.Failure -> CrewErrorText.of(r.error)
            }
        }
    }

    /** 입력 칸 변경. 로고 주소는 업로드가 정하므로 화면이 넘긴 옛 값으로 되돌리지 않는다. */
    fun onEditForm(form: CrewFormState) = _state.update { it.copy(editForm = form.copy(logoUrl = it.editForm.logoUrl)) }

    /**
     * 정보 수정에서 고른 로고(JPEG로 줄인 바이트, 크루 만들기와 같은 CrewLogoEncoder)를 미리 보이고 올린다(POST /crew-logos, DV-6).
     * 올리면 받은 주소를 입력에 싣고, 실패하면 미리보기를 비우고 원래 로고 주소를 둔 채 안내한다.
     */
    fun onEditLogoPicked(jpeg: ByteArray) {
        logoJob?.cancel()
        val original = _state.value.editForm.logoUrl
        _state.update { it.copy(editLogo = jpeg, isUploadingLogo = true) }
        logoJob = viewModelScope.launch {
            when (val r = crews.uploadLogo(jpeg)) {
                is Outcome.Success -> _state.update {
                    it.copy(isUploadingLogo = false, editForm = it.editForm.copy(logoUrl = r.value))
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        isUploadingLogo = false,
                        editLogo = null,
                        editForm = it.editForm.copy(logoUrl = original),
                        message = CrewErrorText.of(r.error)
                    )
                }
            }
        }
    }

    /** 정보 수정에서 고른 사진을 읽지 못했다(디코딩 실패). */
    fun onEditLogoUnreadable() = _state.update { it.copy(message = uiText(R.string.crew_create_logo_failed)) }

    /** 크루 정보 수정 저장(FR-113). 되면 크루 정보 쪽으로 돌아가 크루 정보를 다시 읽는다. 로고를 올리는 동안은 저장하지 않는다. */
    fun onEditSave() {
        val form = _state.value.editForm
        // 긴 옛 크루명은 그대로 두면 통과, 바꿀 때만 12자 검사(CrewEditName, 서버 S2·S3).
        if (!form.isEditValid(_state.value.detail?.name.orEmpty()) || _state.value.isUploadingLogo) return
        act {
            val r = crews.changeInfo(_state.value.crewId, form.toForm())
            if (r is Outcome.Success) {
                _state.update { it.copy(page = CrewHomePage.INFO, editLogo = null) }
                reloadCrew()
            }
            r.done(uiText(R.string.crew_manage_info_changed))
        }
    }

    /**
     * 탈퇴·크루 삭제가 됐다(DV-7): 떠난 크루의 상태와 읽기를 모두 버리고 크루 탭으로 나가라고 알린다. 같은 크루 홈 항목이 남아 있어도
     * 다음 bind가 처음부터 읽는다.
     */
    private fun leftCrew() {
        listOf(refreshJob, detailJob, moreJob, periodJob, logoJob).forEach { it?.cancel() }
        bound = null
        resumedOnce = false
        _state.value = CrewHomeUiState()
        _events.trySend(CrewHomeEvent.Left)
    }

    /** 처리 한 번: 바쁜 동안 다시 받지 않고 [block]이 돌려준 문구를 안내한다. */
    private fun act(block: suspend () -> UiText?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            var message: UiText? = null
            try {
                message = block()
            } finally {
                _state.update { it.copy(isBusy = false, message = message ?: it.message) }
            }
        }
    }

    private fun Outcome<Unit>.done(success: UiText): UiText = when (this) {
        is Outcome.Success -> success
        is Outcome.Failure -> CrewErrorText.of(error)
    }

    /** 역할·인원이 바뀐 뒤: 크루 정보와(읽어 둔 경우) 크루원을 다시 읽는다. */
    private suspend fun reloadCrew() {
        val r = crews.detail(_state.value.crewId)
        if (r is Outcome.Success) _state.update { it.copy(detail = r.value) }
        if (_state.value.members != null) reloadMembers()
    }

    private suspend fun reloadMembers() {
        when (val r = crews.members(_state.value.crewId)) {
            is Outcome.Success -> {
                _state.update { it.copy(members = r.value) }
                readMemberFaces(r.value)
            }

            is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
        }
    }

    /**
     * 모집글마다 대기실 단추 상태를 정하고, [isTicking]이면 시각이 지나 바뀔 수 있는 글(열리기 전·열림)이 남아 있는 동안 1초마다
     * 다시 정한다. 목록·탭·쪽·화면 상태가 바뀌면 collectLatest가 이 반복을 새로 시작한다. 강조 카드의 남은 시간을 위해 지금 서버
     * 시각(scheduleNow)도 함께 갱신한다.
     */
    private suspend fun watchLobbies(runs: PostList?, isTicking: Boolean) {
        val infos = runs?.posts.orEmpty().mapNotNull { post -> post.crewRun?.let { post.postId to it } }
        while (true) {
            val now = serverClock.now()
            val entries = infos.associate { (postId, info) -> postId to LobbyAvailability.of(info, now) }
            // 강조 카드의 남은 시간(F-11)도 같은 서버 시각으로 1초마다 센다.
            _state.update { it.copy(lobbyEntries = entries, scheduleNow = now) }
            if (!isTicking || entries.values.all { it == LobbyAvailability.Closed }) return
            delay(LOBBY_TICK_MILLIS)
        }
    }

    /** 응답·삭제·글쓰기·변경 신호 뒤 갱신. 이어 받아 둔 범위까지 다시 읽는다(postsCovering). 실패하면 지금 목록을 두고 안내한다. */
    private suspend fun reloadPosts() {
        readCovering()?.let { error -> _state.update { it.copy(message = CrewErrorText.of(error)) } }
    }

    /** 크루런 탭의 읽은 범위를 첫 쪽부터 다시 읽어 바꾼다(날짜순, 중복 없음). 실패면 그 오류. */
    private suspend fun readCovering(): AppError? {
        moreJob?.cancel()
        _state.update { it.copy(isLoadingMore = false) }
        val crewId = _state.value.crewId
        val loaded = _state.value.crewRuns?.posts?.size ?: 0
        return when (val r = coveringPages(loaded) { cursor -> crews.activeCrewRuns(crewId, cursor) }) {
            is Outcome.Success -> {
                _state.update { it.copy(crewRuns = r.value) }
                null
            }

            is Outcome.Failure -> r.error
        }
    }

    private companion object {
        /** 강조 카드 결과를 읽는 최대 횟수(끝냈는데 내 기록이 아직 결과에 없을 때 다시 읽기 포함). */
        const val HERO_READS = 4

        /** 강조 카드 결과 다시 읽기 간격. */
        const val HERO_REREAD_MILLIS = 3_000L

        const val LOBBY_TICK_MILLIS = 1_000L

        /** 새로 반영된 거리 강조 시간(설계 05-32 [임시:D-성-889]). */
        const val HIGHLIGHT_MILLIS = 2_000L

        /** 서버 400(커서 기준을 복원할 수 없음). */
        const val BAD_REQUEST = "BAD_REQUEST"
    }
}

/** [postId] 크루런의 내 응답을 [answer]로 바꾼 목록. */
private fun PostList.withAnswer(postId: Long, answer: AttendanceAnswer?): PostList = copy(
    posts = posts.map { post ->
        if (post.postId != postId) post else post.copy(crewRun = post.crewRun?.copy(myAnswer = answer))
    }
)
