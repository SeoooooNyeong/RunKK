package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PendingRequests
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

    private var resumedOnce = false

    /** 화면에 돌아올 때(주간 공동 목표 화면 등에서 복귀) 다시 읽는다. 처음 표시는 bind가 읽는다. */
    fun onResumed() {
        if (resumedOnce && bound != null) load()
        resumedOnce = true
    }

    /** 크루 정보·가입 신청·크루원을 읽는다. 정보 수정 칸은 처음 읽을 때만 채운다(쓰던 값을 덮지 않는다). */
    fun load() {
        val crewId = _state.value.crewId
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val detail = crews.detail(crewId)
            val requests = crews.pendingRequests(crewId)
            val members = crews.members(crewId)
            val race = crews.race(crewId, null)
            _state.update { st ->
                val info = (detail as? Outcome.Success)?.value
                st.copy(
                    isLoading = false,
                    detail = info ?: st.detail,
                    crewName = info?.name ?: st.crewName,
                    form = if (st.detail == null && info != null) info.toFormState() else st.form,
                    inviteCode = st.inviteCode ?: info?.inviteCode,
                    // 일부만 실패하면 앞서 읽은 값을 지우지 않는다.
                    requests = (requests as? Outcome.Success)?.value ?: st.requests,
                    members = (members as? Outcome.Success)?.value ?: st.members,
                    weeklyGoal = (race as? Outcome.Success)?.value?.goal ?: st.weeklyGoal,
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

    /** 운영진을 크루원으로 해제한다(FR-204, 크루장만). */
    fun onDismissManager(member: CrewMembers.Member) = act {
        _state.update { it.copy(menuMember = null) }
        crews.dismissManager(_state.value.crewId, member.memberId)
            .done(uiText(R.string.crew_manage_dismissed, member.displayName))
    }

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form) }

    /** 크루원 메뉴(와이어 I10). 메뉴에서 고르면 시트를 닫고 그 행동으로 간다. */
    fun onMenu(member: CrewMembers.Member) = _state.update { it.copy(menuMember = member) }

    fun onMenuDismiss() = _state.update { it.copy(menuMember = null) }

    /** 크루 정보 수정 화면(와이어 I9)을 지금 크루 정보로 채워 연다. */
    fun onInfoOpen() = _state.update { it.copy(isEditingInfo = true, form = it.detail?.toFormState() ?: it.form) }

    fun onInfoClose() = _state.update { it.copy(isEditingInfo = false) }

    fun onCopied() = _state.update { it.copy(message = uiText(R.string.crew_manage_invite_copied)) }

    /** 정보 수정(FR-113). 저장되면 수정 화면을 닫는다. */
    fun onSaveInfo() {
        val form = _state.value.form
        if (!form.isValid) return
        act {
            val r = crews.changeInfo(_state.value.crewId, form.toForm())
            if (r is Outcome.Success) _state.update { s -> s.copy(crewName = form.name.trim(), isEditingInfo = false) }
            r.done(uiText(R.string.crew_manage_info_changed))
        }
    }

    fun onAsk(action: ManageAction) = _state.update { it.copy(confirming = action, menuMember = null) }

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

                is ManageAction.Appoint ->
                    crews.appointManager(crewId, action.member.memberId)
                        .done(uiText(R.string.crew_manage_appointed, action.member.displayName))

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
