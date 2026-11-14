package com.a207.runkk.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatRepository
import com.a207.runkk.app.domain.chat.ChatRoom
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루 채팅방 화면 상태(6.1.14 ChatViewModel). 화면이 보일 때 ChatRoom.open(crewId), 사라질 때 close()를 부르고(열린 방 알림 억제는
 * ChatRoom이 OpenChatRoomRegistry로 알린다, D-130) ChatRoom.messages를 화면 상태로 바꾼다. 보내기는 앞뒤 공백을 뺀 1~500자
 * (코드 포인트)만 ChatRoom.send로 넘기고, 실패한 메시지는 같은 clientMessageId로 다시 보낸다(D-160 (3)). 리액션은 이미 단 종류면 떼고
 * 아니면 단다(FR-210). 삭제는 canDelete인 메시지만 확인 뒤(FR-213), 신고는 남의 메시지만 사유를 골라 ReportRepository로 보낸다
 * (FR-214, 기타는 상세 1~200자, D-149). 업무 규칙은 ChatRoom·서버가 갖는다.
 * 남이 보낸 메시지의 작성자와 반응자의 얼굴 그림은 얼굴 키마다 한 번 받는다([임시:D-성-236]). 받지 못해도 안내를 띄우지 않고 기본
 * 얼굴로 둔다([임시:FR-성-24]).
 */
@HiltViewModel
class ChatViewModel
@Inject
constructor(
    private val room: ChatRoom,
    private val chats: ChatRepository,
    private val reports: ReportRepository
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    /** 이미 받았거나 받는 중인 얼굴 키(키마다 한 번 받는다). 실패한 키는 빼서 다음 목록 갱신에 다시 받는다. */
    private val requestedFaces = mutableSetOf<String>()

    val state: StateFlow<ChatUiState> =
        combine(local, room.messages) { s, page -> s.copy(messages = page.messages, hasMore = page.hasMore) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        // 리액션·삭제·이전 쪽 읽기 실패를 안내 줄로 알린다(A4). 알려진 거부 사유면 그 문구, 아니면 동작별 문구다.
        viewModelScope.launch {
            room.failures.collect { failure -> local.update { it.copy(message = failure.toText()) } }
        }
        // 남이 보낸 보통 글의 작성자 얼굴(내 메시지와 시스템 메시지는 원을 그리지 않는다).
        viewModelScope.launch {
            room.messages.collect { page ->
                readFaces(page.messages.filter { !it.isMine && !it.isSystem }.mapNotNull { it.authorFaceKey })
            }
        }
    }

    /**
     * 얼굴 키마다 셀카 얼굴 부위 그림을 한 번 받는다(크루원 목록 CrewHomeViewModel.readMemberFaces와 같은 방식, 같은 캐시). 받아 둔
     * 그림은 바로 쓰고 없는 것만 받는다. 실패는 알리지 않는다(기본 얼굴로 그린다).
     */
    private fun readFaces(faceKeys: List<String>) {
        val keys = faceKeys.distinct().filter { requestedFaces.add(it) }
        val cached = keys.mapNotNull { key ->
            chats.cachedFaceLayers(key)?.takeIf { it.isNotEmpty() }?.let { key to it }
        }
        if (cached.isNotEmpty()) local.update { it.copy(faces = it.faces + cached) }
        keys.filter { key -> cached.none { it.first == key } }.forEach { key ->
            viewModelScope.launch {
                when (val r = chats.faceLayers(key)) {
                    is Outcome.Success -> r.value.takeIf { it.isNotEmpty() }?.let { layers ->
                        local.update { it.copy(faces = it.faces + (key to layers)) }
                    }

                    is Outcome.Failure -> requestedFaces.remove(key)
                }
            }
        }
    }

    /** 화면이 보인다: 방을 연다(같은 방이 이미 열려 있으면 ChatRoom이 무시한다). */
    fun onShown(crewId: Long) {
        local.update { it.copy(crewId = crewId) }
        room.open(crewId)
    }

    /** 화면이 사라진다(다른 화면·앱 뒤로): 퇴장 신호를 보내고 자기 임대만 반납한다(D-103). */
    fun onHidden() = room.close()

    override fun onCleared() {
        room.close()
    }

    /** 입력은 그대로 두고 보낼 수 있는지만 따진다(500자를 넘기면 넘었다고 보인다). */
    fun onDraft(text: String) = local.update { it.copy(draft = text) }

    fun onSend() {
        val s = local.value
        if (!s.canSend) return
        room.send(s.draft.trim())
        local.update { it.copy(draft = "") }
    }

    fun onRetry(message: ChatMessage) {
        if (message.sendState != ChatMessage.SendState.FAILED) return
        message.clientMessageId?.let(room::retry)
    }

    fun onReaction(message: ChatMessage, kind: ReactionKind) {
        val id = message.messageId ?: return
        if (message.isRemoved) return
        if (kind in message.myReactions) room.unreact(id, kind) else room.react(id, kind)
    }

    fun onLoadOlder() = room.loadOlder()

    // 반응한 사람(피그마 F-09d, D-성-24)

    /** 서버에 있는 보이는 메시지만 연다. [tab]은 길게 누른 알약의 종류(null = 전체). */
    fun onOpenReactors(message: ChatMessage, tab: ReactionKind?) {
        val id = message.messageId ?: return
        if (message.isRemoved) return
        local.update {
            it.copy(
                reactorsTarget = message,
                reactorsTab = tab,
                reactors = emptyList(),
                isLoadingReactors = true,
                isReactorsFailed = false
            )
        }
        viewModelScope.launch {
            val result = chats.reactors(id)
            if (result is Outcome.Success) readFaces(result.value.mapNotNull { it.faceKey })
            local.update { s ->
                if (s.reactorsTarget?.messageId != id) return@update s
                when (result) {
                    is Outcome.Success -> s.copy(reactors = result.value, isLoadingReactors = false)
                    is Outcome.Failure -> s.copy(isLoadingReactors = false, isReactorsFailed = true)
                }
            }
        }
    }

    fun onReactorTab(tab: ReactionKind?) = local.update { it.copy(reactorsTab = tab) }

    fun onCloseReactors() = local.update {
        it.copy(
            reactorsTarget = null,
            reactorsTab = null,
            reactors = emptyList(),
            isLoadingReactors = false,
            isReactorsFailed = false
        )
    }

    // 삭제(FR-213)

    fun onDeleteAsk(message: ChatMessage) {
        if (message.canDelete && message.messageId != null && !message.isRemoved) {
            local.update { it.copy(confirmDelete = message) }
        }
    }

    fun onDeleteDismiss() = local.update { it.copy(confirmDelete = null) }

    fun onDeleteConfirm() {
        val id = local.value.confirmDelete?.messageId ?: return
        local.update { it.copy(confirmDelete = null) }
        room.delete(id)
    }

    // 신고(FR-214)

    /** 남의 메시지만 신고한다(본인 콘텐츠는 서버가 TARGET_INVALID로 거부한다, FR-214). */
    fun onReportAsk(message: ChatMessage) {
        if (message.isMine || message.isRemoved || message.isSystem || message.messageId == null) return
        local.update { it.copy(reportTarget = message, reportReason = null, reportDetail = "") }
    }

    fun onReportReason(reason: ReportReason) = local.update { it.copy(reportReason = reason) }

    /** 상세는 200자(코드 포인트)까지만 받는다. */
    fun onReportDetail(text: String) = local.update { it.copy(reportDetail = text.takeCodePoints(REPORT_MAX)) }

    fun onReportDismiss() = local.update { it.copy(reportTarget = null, isReporting = false) }

    fun onReportSubmit() {
        val s = local.value
        val id = s.reportTarget?.messageId ?: return
        val reason = s.reportReason ?: return
        if (!s.canSubmitReport) return
        local.update { it.copy(isReporting = true) }
        viewModelScope.launch {
            val target = ReportTarget(ReportTargetKind.CHAT_MESSAGE, id)
            val result = reports.report(target, reason, s.reportDetail.trim().ifBlank { null })
            // 네트워크·서버 실패는 대화상자를 열어 둬 다시 보낼 수 있게 하고, 접수·업무 거부면 닫는다.
            val isRetryable = result is Outcome.Failure && result.error !is AppError.Rejected
            val message = when (result) {
                is Outcome.Success -> uiText(R.string.chat_report_done)
                is Outcome.Failure -> ChatErrorText.of(result.error)
            }
            local.update {
                if (it.reportTarget?.messageId != id) {
                    it.copy(message = message)
                } else {
                    it.copy(
                        reportTarget = if (isRetryable) it.reportTarget else null,
                        isReporting = false,
                        message = message
                    )
                }
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private companion object {
        const val REPORT_MAX = ChatUiState.REPORT_DETAIL_MAX
    }
}

/** 실패 안내 문구: 채팅 거부 사유표(ChatErrorText.REJECTED)에 있으면 그 문구, 없으면 동작별 문구. */
private fun ChatRoom.Failure.toText(): UiText {
    val code = (error as? AppError.Rejected)?.code
    code?.let(ChatErrorText.REJECTED::get)?.let { return UiText.Res(it) }
    return UiText.Res(
        when (action) {
            ChatRoom.Failure.Action.REACT -> R.string.chat_react_failed
            ChatRoom.Failure.Action.DELETE -> R.string.chat_delete_failed
            ChatRoom.Failure.Action.LOAD_OLDER -> R.string.chat_load_older_failed
        }
    )
}
