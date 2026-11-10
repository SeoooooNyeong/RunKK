package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration

data class CrewRunUiState(
    val postId: Long = 0,
    /**
     * 크루런 거리(대기실 응답, 다시 들어오면 결과 응답). 대기실([lobby])이 있는데 null이면 거리 「제한 없음」 크루런이다([임시:D-성-28]).
     */
    val distance: Distance? = null,
    val phase: CrewRunPhase = CrewRunPhase.ENTERING,
    val lobby: Lobby? = null,
    /** 내 준비 완료 여부(대기실 응답의 내 줄, me). */
    val isReady: Boolean = false,
    /** 모집 일시까지 남은 시간. */
    val untilStart: Duration? = null,
    val countdown: Long? = null,
    /** 중계의 크루원 표(내 줄 포함, 대기실 입장 순). */
    val relay: List<RelayRow> = emptyList(),
    /** 크루가 함께 달린 거리 합(중계). */
    val totalDistance: Distance = Distance.ZERO,
    /** 내 측정값(거리·경과·페이스·정지·경로선). */
    val mine: CrewRunProgress = CrewRunProgress.ZERO,
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
    val canRetry: Boolean = true,
    /**
     * 대기실 참가자 셀카 얼굴 부위 그림(faceKey별, [임시:D-성-12]). 들어온 참가자만 받고, 받기 전이거나 기본 캐릭터 얼굴이면 없다
     * (얼굴 키·표시 성별로 기본 얼굴을 그린다).
     */
    val mateFaces: Map<String, List<FaceLayer>> = emptyMap()
) {
    val isPaused: Boolean get() = mine.isPaused

    /** "함께 달리는 크루원 n명": 나를 빼고 아직 끝내지 않은 크루원 수. */
    val runningMates: Int get() = relay.count { !it.isMe && it.isRunning }
}
