package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration

data class CrewRunUiState(
    val postId: Long = 0,
    /** 크루런 거리(대기실 응답). 들어오기 전이면 null. */
    val distance: Distance? = null,
    val phase: CrewRunPhase = CrewRunPhase.ENTERING,
    val lobby: Lobby? = null,
    /** 내 준비 완료 여부(대기실 응답의 내 줄, me). */
    val isReady: Boolean = false,
    /** 모집 일시까지 남은 시간. */
    val untilStart: Duration? = null,
    val countdown: Long? = null,
    val relay: List<RelayRow> = emptyList(),
    val cancelledReason: String? = null,
    val isConfirmingQuit: Boolean = false,
    val isLeaving: Boolean = false,
    val hint: UiText? = null,
    val isBusy: Boolean = false,
    val error: UiText? = null,
    /**
     * 대기실에 들어가지 못한 오류([error])를 다시 시도해 볼 만한지. 업무 규칙 거부(열리지 않음·이미 시작·참가자 아님 등)는
     * 다시 해도 같으므로 거짓이고, 화면은 "다시 시도" 대신 "돌아가기"를 보인다.
     */
    val canRetry: Boolean = true
)
