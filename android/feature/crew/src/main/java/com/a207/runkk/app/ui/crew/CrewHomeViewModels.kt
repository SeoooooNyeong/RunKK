package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 크루 홈 탭(시안 HiMyCrew): 레이스·크루런·응원보드·크루원. */
enum class CrewTab(val label: String) { RACE("레이스"), CREW_RUN("크루런"), CHEER("응원보드"), MEMBERS("크루원") }

data class CrewHomeUiState(
    val crewId: Long = 0,
    val crewName: String = "",
    val tab: CrewTab = CrewTab.RACE,
    val loading: Boolean = false,
    val race: RaceStatus? = null,
    val period: CrewRanking.Period = CrewRanking.Period.WEEKLY,
    val ranking: CrewRanking? = null,
    val crewRuns: PostList? = null,
    val cheers: PostList? = null,
    val members: CrewMembers? = null,
    val cheerDraft: String = "",
    /** 내가 고른 참여 응답(이 화면에서 보낸 것). 서버 목록에 내 응답 항목이 아직 없다. */
    val votes: Map<Long, VoteAnswer> = emptyMap(),
    val confirmLeave: Boolean = false,
    val editingIntro: Boolean = false,
    val introDraft: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val cheerValid: Boolean get() = cheerDraft.isNotBlank() && cheerDraft.length <= CHEER_MAX

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
 * 탭을 처음 열 때 그 탭의 포트만 부른다(레이스 race + crewRanking, 크루런 posts(CREW_RUN), 응원보드 posts(CHEER), 크루원 members).
 */
@HiltViewModel
class CrewHomeViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewHomeUiState())
    val state: StateFlow<CrewHomeUiState> = _state.asStateFlow()

    private val _events = Channel<CrewHomeEvent>(Channel.BUFFERED)
    val events: Flow<CrewHomeEvent> = _events.receiveAsFlow()

    private var bound: Long? = null

    fun bind(key: Routes.CrewHome) {
        if (bound == key.crewId) return
        bound = key.crewId
        _state.value = CrewHomeUiState(crewId = key.crewId, crewName = key.crewName)
        refresh()
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
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
            _state.update { it.copy(loading = false, error = error?.let(CrewErrorText::of)) }
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
        if (!s.cheerValid || s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = crews.writeCheer(s.crewId, s.cheerDraft.trim())) {
                is Outcome.Success -> {
                    _state.update { it.copy(busy = false, cheerDraft = "") }
                    reloadPosts(PostList.Kind.CHEER)
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 글 삭제(작성자 본인, FR-130·FR-134). 크루장의 남의 글 삭제는 크루 관리에서 권한을 확인한 뒤 서버가 판정한다. */
    fun onDeletePost(item: PostList.Item) {
        if (!item.mine || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = crews.deletePost(item.postId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(busy = false, message = "글을 지웠어요") }
                    reloadPosts(item.kind)
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    // 크루런

    /** 갈래요·못 가요(FR-145). 마감 전에는 바꿀 수 있다. */
    fun onVote(item: PostList.Item, answer: VoteAnswer) {
        viewModelScope.launch {
            when (val r = crews.vote(item.postId, answer)) {
                is Outcome.Success -> _state.update { it.copy(votes = it.votes + (item.postId to answer)) }
                is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
            }
        }
    }

    // 크루원·탈퇴·내 소개

    fun onLeaveAsk() = _state.update { it.copy(confirmLeave = true) }

    fun onLeaveDismiss() = _state.update { it.copy(confirmLeave = false) }

    /** 확인 뒤 탈퇴(FR-125). 크루원이 남은 크루장이면 서버가 양도를 안내한다(FR-126). */
    fun onLeaveConfirm() {
        val crewId = _state.value.crewId
        _state.update { it.copy(confirmLeave = false, busy = true) }
        viewModelScope.launch {
            when (val r = crews.leave(crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(busy = false) }
                    _events.trySend(CrewHomeEvent.Left)
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onIntroEdit() = _state.update { it.copy(editingIntro = true, introDraft = "") }

    fun onIntroDraft(text: String) = _state.update { it.copy(introDraft = text.take(CrewHomeUiState.INTRO_MAX)) }

    fun onIntroDismiss() = _state.update { it.copy(editingIntro = false) }

    /** 크루 내 한줄 소개(FR-144, 40자). */
    fun onIntroSave() {
        val s = _state.value
        _state.update { it.copy(editingIntro = false, busy = true) }
        viewModelScope.launch {
            when (val r = crews.editCrewProfile(s.crewId, s.introDraft.trim())) {
                is Outcome.Success -> {
                    _state.update { it.copy(busy = false, message = "크루 내 소개를 바꿨어요", members = null) }
                    if (_state.value.tab == CrewTab.MEMBERS) refresh()
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, message = CrewErrorText.of(r.error)) }
            }
        }
    }

    private suspend fun reloadPosts(kind: PostList.Kind) {
        when (val r = crews.posts(_state.value.crewId, kind)) {
            is Outcome.Success -> _state.update {
                if (kind == PostList.Kind.CHEER) it.copy(cheers = r.value) else it.copy(crewRuns = r.value)
            }

            is Outcome.Failure -> _state.update { it.copy(message = CrewErrorText.of(r.error)) }
        }
    }
}

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
    val loading: Boolean = true,
    val requests: PendingRequests? = null,
    val members: CrewMembers? = null,
    val inviteCode: String? = null,
    val form: CrewFormState = CrewFormState(),
    val confirming: ManageAction? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

sealed interface CrewManageEvent {
    /** 크루를 지웠다: 크루 탭으로. */
    data object Deleted : CrewManageEvent
}

/**
 * 크루 관리(FR-113~FR-116, FR-123, FR-127, FR-136). 권한은 서버가 검사한다(FR-128): 크루장이 아니면 거부 문구를 보인다.
 * 정보 수정 칸은 지금 크루 정보를 돌려주는 조회가 없어 이름만 채워 둔다.
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
        _state.value =
            CrewManageUiState(crewId = key.crewId, crewName = key.crewName, form = CrewFormState(name = key.crewName))
        load()
    }

    fun load() {
        val crewId = _state.value.crewId
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val requests = crews.pendingRequests(crewId)
            val members = crews.members(crewId)
            _state.update {
                it.copy(
                    loading = false,
                    requests = (requests as? Outcome.Success)?.value,
                    members = (members as? Outcome.Success)?.value,
                    error = ((requests as? Outcome.Failure) ?: (members as? Outcome.Failure))?.error?.let(
                        CrewErrorText::of
                    )
                )
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onApprove(request: PendingRequests.Request) = decide(request, approve = true)

    fun onReject(request: PendingRequests.Request) = decide(request, approve = false)

    private fun decide(request: PendingRequests.Request, approve: Boolean) = act {
        val r = if (approve) crews.approveJoin(request.requestId) else crews.rejectJoin(request.requestId)
        r.done(if (approve) "${request.displayName}님을 받았어요" else "${request.displayName}님의 신청을 거절했어요")
    }

    /** 초대 코드 재발급(FR-114): 이전 코드는 바로 무효, 새 코드는 7일 유효. */
    fun onReissue() = act {
        when (val r = crews.reissueInviteCode(_state.value.crewId)) {
            is Outcome.Success -> {
                _state.update { it.copy(inviteCode = r.value) }
                "새 초대 코드를 만들었어요. 7일 동안 쓸 수 있어요"
            }

            is Outcome.Failure -> CrewErrorText.of(r.error)
        }
    }

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form) }

    /** 정보 수정(FR-113). */
    fun onSaveInfo() {
        val form = _state.value.form
        if (!form.valid) return
        act {
            crews.changeInfo(_state.value.crewId, form.toForm()).done("크루 정보를 바꿨어요").also {
                _state.update { s -> s.copy(crewName = form.name.trim()) }
            }
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
                ).done("${action.member.displayName}님을 내보냈어요")

                is ManageAction.Transfer ->
                    crews.transferLeadership(
                        crewId,
                        action.member.memberId
                    ).done("${action.member.displayName}님에게 크루장을 넘겼어요")

                ManageAction.Delete -> when (val r = crews.delete(crewId)) {
                    is Outcome.Success -> {
                        _events.trySend(CrewManageEvent.Deleted)
                        null
                    }

                    is Outcome.Failure -> CrewErrorText.of(r.error)
                }
            }
        }
    }

    /** 처리 한 번: [block]이 돌려준 문구를 안내하고 목록을 다시 읽는다. */
    private fun act(block: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = block()
            _state.update { it.copy(busy = false, message = message) }
            load()
        }
    }

    private fun Outcome<Unit>.done(success: String): String = when (this) {
        is Outcome.Success -> success
        is Outcome.Failure -> CrewErrorText.of(error)
    }
}
