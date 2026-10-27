package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루 콘텐츠 신고(FR-214, D-104, D-149): 응원글·일정(크루런·대면 모임)·공지·투표·게시글 하나를 사유(욕설비방·음란·광고스팸·
 * 개인정보노출·기타)와 함께 ReportRepository로 보낸다. 기타는 상세 1~200자가 필요하다. 본인 글은 열지 않는다(서버 TARGET_INVALID).
 * 같은 대상을 다시 신고하면 서버가 ALREADY_REPORTED로 거부한다. 크루 홈의 탭들이 이 ViewModel 하나를 함께 쓴다.
 */
@HiltViewModel
class CrewReportViewModel @Inject constructor(private val reports: ReportRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewReportUiState())
    val state: StateFlow<CrewReportUiState> = _state.asStateFlow()

    fun onAsk(item: PostList.Item) {
        if (item.isMine) return
        _state.update {
            CrewReportUiState(
                target = ReportTarget(item.kind.reportKind(), item.postId),
                targetAuthor = item.authorName,
                targetKind = item.kind
            )
        }
    }

    fun onReason(reason: ReportReason) = _state.update { it.copy(reason = reason) }

    /** 상세는 200자(코드 포인트)까지만 받는다. */
    fun onDetail(text: String) = _state.update { it.copy(detail = text.takeCodePoints(CrewReportUiState.DETAIL_MAX)) }

    fun onDismiss() = _state.update { it.copy(target = null, isSubmitting = false) }

    fun onSubmit() {
        val s = _state.value
        val target = s.target ?: return
        val reason = s.reason ?: return
        if (!s.canSubmit) return
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val r = reports.report(target, reason, s.detail.trim().ifBlank { null })
            // 네트워크·서버 실패는 사유·내용을 남겨 다시 보낼 수 있게 하고, 접수·업무 거부(이미 신고함 등)면 닫는다.
            val isRetryable = r is Outcome.Failure && r.error !is AppError.Rejected
            val message = when (r) {
                is Outcome.Success -> uiText(R.string.crew_report_done)
                is Outcome.Failure -> CrewErrorText.of(r.error)
            }
            _state.update {
                if (it.target != target) {
                    it.copy(message = message)
                } else {
                    it.copy(target = if (isRetryable) target else null, isSubmitting = false, message = message)
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}

/** 게시 종류 → 신고 대상 종류(FR-214). 크루런·대면 모임은 일정(SCHEDULE)이다. */
internal fun PostList.Kind.reportKind(): ReportTargetKind = when (this) {
    PostList.Kind.CHEER -> ReportTargetKind.CHEER
    PostList.Kind.CREW_RUN, PostList.Kind.MEETUP -> ReportTargetKind.SCHEDULE
    PostList.Kind.NOTICE -> ReportTargetKind.NOTICE
    PostList.Kind.POLL -> ReportTargetKind.POLL
    PostList.Kind.BOARD -> ReportTargetKind.BOARD
}
