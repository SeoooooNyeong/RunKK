package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.MyCrew
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루 탭(FR-117~FR-122, FR-124, FR-143, 피그마 F-01~F-03). 들어오면 GET /crews/me로 소속을 확인해 크루가 있으면 내 크루 홈을,
 * 없으면 크루 찾기(대기 중인 내 가입 신청·취소 포함)를 보인다. 찾기는 공개 크루 목록(조건 태그 AND, 끝까지 내리면 다음 쪽)을 보이고,
 * 크루 상세에서 바로 가입(자유)·가입 신청(승인제)을 한다. 초대 코드는 바텀시트에서 코드로 크루를 먼저 조회하고(미리보기) 확인 뒤
 * 가입한다(contract-W4 4절). 크루를 나온 뒤 24시간은 상세 가입 단추 자리에 남은 시간을 보이고 크루 만들기를 막는다(F-02c).
 * 가입되면(JOINED) 그 자리에서 내 크루 홈으로 바뀐다. 비공개 크루는 초대 코드로만 들어온다(Q-38).
 */
@HiltViewModel
class CrewFindViewModel internal constructor(private val crews: CrewRepository, clock: Clock) : ViewModel() {
    @Inject
    constructor(crews: CrewRepository) : this(crews, Clock.systemUTC())

    private val _state = MutableStateFlow(CrewFindUiState())
    val state: StateFlow<CrewFindUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var moreJob: Job? = null
    private var mineJob: Job? = null
    private var detailJob: Job? = null
    private var previewJob: Job? = null
    private val cooldown = JoinCooldown(clock)

    /** 찾기 목록(첫 쪽)을 한 번이라도 읽기 시작했는가. 공개 크루가 0개여도 돌아올 때마다 다시 읽지 않게 한다(리뷰 N-12). */
    private var hasRequestedFind = false

    init {
        loadMine()
    }

    /** 크루 로고 바이트(크루 이미지가 읽는다). */
    suspend fun logo(url: String): Outcome<ByteArray> = crews.logo(url)

    /**
     * 다른 화면(크루 만들기·검색 등)에서 크루 탭으로 돌아왔다. 탭 항목과 ViewModel이 그대로 남아 있어(selectTab이 같은 키를 다시 넣는다)
     * 그 사이 생긴 소속을 모르므로 조용히 다시 확인한다. 이미 내 크루를 보이거나 처음 확인 중이면 하지 않는다(E2E 시나리오 5에서 찾은 결함).
     */
    fun refreshMine() {
        val s = _state.value
        if (s.mine == null && !s.isCheckingMine) loadMine(isQuiet = true)
    }

    /**
     * 이 탭 자리에 그린 내 크루 홈에서 탈퇴·크루 삭제했다(DV-7). 탭 항목과 ViewModel이 남아 [refreshMine]이 다시 확인하지 않으므로
     * 내 크루를 비우고 처음처럼 다시 확인해 크루 찾기(나온 뒤 24시간 카운트다운 포함)를 보인다.
     */
    fun onLeftCrew() {
        _state.update { it.copy(mine = null) }
        loadMine()
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
                    startCooldown(if (crew == null) r.value.joinableAt else null)
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

    /** 가입 가능 시각까지 1초마다 남은 시간을 줄인다. 시각이 지나면 제한을 지운다. */
    private fun startCooldown(until: Instant?) {
        _state.update { it.copy(joinableAt = until) }
        cooldown.start(viewModelScope, until) { left ->
            _state.update {
                if (left ==
                    null
                ) {
                    it.copy(joinableAt = null, joinRemaining = null)
                } else {
                    it.copy(joinRemaining = left)
                }
            }
        }
    }

    /** 첫 쪽부터 다시 찾는다(조건 적용·새로고침). */
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

    /** 조건 설정 팝업(피그마 F-01b)을 지금 조건으로 채워 연다. */
    fun onConditionOpen() = _state.update { it.copy(isConditionOpen = true, conditionDraft = it.tags) }

    fun onConditionToggle(id: String) =
        _state.update { it.copy(conditionDraft = RunkkTags.toggle(it.conditionDraft, id)) }

    fun onConditionDismiss() = _state.update { it.copy(isConditionOpen = false) }

    /** 고른 조건을 적용해 첫 쪽부터 다시 찾는다(피그마 F-01c). */
    fun onConditionApply() {
        _state.update { it.copy(isConditionOpen = false, tags = it.conditionDraft) }
        load()
    }

    /** 상세를 연다. 개설일·통계·최근 크루런은 크루 정보(GET /crews/{id})로 채운다. 읽지 못하면 빈 값으로 둔다. */
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

    /** 24시간 제한 중에는 크루 만들기 화면을 열지 않고 안내한다(서버도 COOLDOWN으로 막는다). 열어도 되면 참. */
    fun onCreateRequested(): Boolean {
        if (_state.value.joinRemaining == null) return true
        _state.update { it.copy(message = uiText(R.string.crew_error_cooldown)) }
        return false
    }

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

    // 초대 코드(피그마 F-03·F-03b·F-03c): 조회 → 확인 → 가입

    fun onInviteOpen() = _state.update { it.copy(invite = InviteSheetState()) }

    /** 시트를 닫는다. 조회는 멈추지만 이미 보낸 가입은 끝까지 기다려 결과를 반영한다(서버에서 가입됐는데 화면이 모르는 일이 없게). */
    fun onInviteClose() {
        previewJob?.cancel()
        _state.update { it.copy(invite = null) }
    }

    /** 초대 코드는 6자리 영문·숫자(FR-112). 대문자로 맞춘다. 코드를 고치면 입력 단계로 돌아간다. */
    fun onInviteCode(text: String) = _state.update { st ->
        val invite = st.invite ?: return@update st
        if (invite.isBusy) return@update st // 조회·가입을 기다리는 동안은 코드를 바꾸지 않는다
        val code = text.filter { it.isLetterOrDigit() && it.code < ASCII_LIMIT }.take(INVITE_CODE_LENGTH).uppercase()
        if (code == invite.code) st else st.copy(invite = InviteSheetState(code = code))
    }

    /**
     * "확인": 코드로 크루를 조회한다. 없거나 만료면 칸 오류(NOT_FOUND), 시도 제한이면 칸 아래 기다리라는 안내(LIMITED),
     * 찾으면 미리보기(FOUND). 가입은 하지 않는다. 시트 안 오류는 스낵바로 띄우지 않는다(시트·키보드에 가려 보이지 않음).
     */
    fun onInviteConfirm() {
        val invite = _state.value.invite ?: return
        if (!invite.isCodeComplete || invite.isBusy) return
        _state.update { it.copy(invite = invite.copy(isBusy = true)) }
        previewJob = viewModelScope.launch {
            val r = crews.previewByInviteCode(invite.code)
            _state.update { st ->
                val now = st.invite ?: return@update st
                if (now.code != invite.code) return@update st
                when {
                    r is Outcome.Success -> st.copy(
                        invite = now.copy(isBusy = false, step = InviteStep.FOUND, preview = r.value)
                    )

                    r is Outcome.Failure && inviteErrorStep(r.error) != null ->
                        st.copy(invite = now.copy(isBusy = false, step = inviteErrorStep(r.error)!!, preview = null))

                    r is Outcome.Failure -> st.copy(
                        invite = now.copy(isBusy = false),
                        message = CrewErrorText.of(r.error)
                    )

                    else -> st
                }
            }
        }
    }

    /** 미리보기의 공개 크루를 눌렀다: 시트를 닫고 크루 상세를 연다. */
    fun onInvitePreviewOpen(preview: CrewPreview) {
        onInviteClose()
        onOpen(preview.toListItem())
    }

    /** "가입하기"(FR-122): 확인한 코드로 가입한다. 가입되면 응답의 크루로 내 크루 홈을 연다. */
    fun onInviteSubmit() {
        val invite = _state.value.invite ?: return
        if (invite.step != InviteStep.FOUND || invite.isBusy) return
        _state.update { it.copy(invite = invite.copy(isBusy = true)) }
        viewModelScope.launch {
            when (val result = crews.applyWithInviteCode(invite.code)) {
                is Outcome.Success -> {
                    _state.update { it.copy(invite = null) }
                    onJoined(result.value)
                }

                is Outcome.Failure -> _state.update { st ->
                    // 미리보기 뒤 코드가 재발급·만료됐거나 시도 제한이면 다시 입력 단계의 칸 아래 안내로 보인다.
                    val errorStep = inviteErrorStep(result.error)
                    st.copy(
                        invite = st.invite?.let {
                            if (errorStep != null) {
                                it.copy(isBusy = false, step = errorStep, preview = null)
                            } else {
                                it.copy(isBusy = false)
                            }
                        },
                        message = if (errorStep != null) null else CrewErrorText.of(result.error)
                    )
                }
            }
        }
    }

    /** 시트 안 칸 아래에 보일 초대 코드 오류 단계. 그 밖의 실패는 null(스낵바). */
    private fun inviteErrorStep(error: AppError): InviteStep? = when (error) {
        AppError.Rejected(INVALID_CODE) -> InviteStep.NOT_FOUND
        AppError.Rejected(TOO_MANY_ATTEMPTS) -> InviteStep.LIMITED
        else -> null
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
        const val INVALID_CODE = "INVALID_CODE"
        const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
        const val ASCII_LIMIT = 128
    }
}

/** 초대 미리보기 → 크루 상세를 여는 목록 한 줄(크루장·이번 주 거리는 상세가 크루 정보로 채운다). */
internal fun CrewPreview.toListItem(): CrewList.Item = CrewList.Item(
    crewId = crewId,
    name = name,
    intro = intro,
    tags = tags,
    leaderName = "",
    headcount = headcount,
    weeklyDistance = com.a207.runkk.app.core.model.Distance(0),
    joinPolicy = joinPolicy,
    logoUrl = logoUrl
)
