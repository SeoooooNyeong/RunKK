package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunnerLook
import java.time.Instant
import java.time.LocalDate

/**
 * 크루런 대기실(서버 crew.LobbyView, FR-147). [distance]는 크루런 거리이고 null이면 제한 없음이다(W6).
 * 설계 05-32 5.2의 추가 항목: [phase]는 논리 단계(옛 서버면 null), [startAt]은 출발 예약의 측정 시작 절대시각(예약 시각 + 3초, 앱은
 * 다시 3초를 더하지 않는다), [countdownParticipantIds]는 예약 순간 고정한 출발 후보, [canStartNow]·[canJoin]·[isMeRunner]는 열람자 기준
 * 「지금 시작」 가능·중간 참여 가능·진행 참가자 여부, [measurementRecordId]는 진행 참가자일 때 내 측정 기록 ID(서버 예약 UUID)다.
 */
data class Lobby(
    val postId: Long,
    val participants: List<Participant>,
    val meetAt: Instant,
    val distance: Distance?,
    val crewId: Long? = null,
    val revision: Long = 0,
    val phase: LobbyPhase? = null,
    val countdownToken: String? = null,
    val startAt: Instant? = null,
    val countdownParticipantIds: List<Long> = emptyList(),
    val canStartNow: Boolean = false,
    val canJoin: Boolean = false,
    val isMeRunner: Boolean = false,
    val measurementRecordId: String? = null
) {
    /** 열람자 본인 줄. 갈래요 응답도 입장도 안 했으면 없다. */
    val me: Participant? get() = participants.firstOrNull { it.isMe }

    /** 출발 예약의 고정 후보에 내가 들어 있다(카운트다운이 끝나면 내 측정이 시작된다). */
    val isMeCandidate: Boolean get() = me?.memberId?.let { it in countdownParticipantIds } == true

    /** 내가 아직 대기실에 들어오지 않았다(줄이 없거나 미입장). 대기실 단계에서만 입장(enter)을 보낸다. */
    val isEnterNeeded: Boolean get() = me?.isEntered != true

    /**
     * [isMe]는 열람자 본인 줄이다. [isEntered]가 거짓이면 갈래요 응답만 하고 대기실에 들어오지 않았다(미입장).
     * [faceKey]·[shownGender]는 그 회원의 아바타 얼굴 키와 표시 성별(D-327)이고 옛 서버·얼굴 없음이면 null이다(크루원 아바타).
     */
    data class Participant(
        val displayName: String,
        val isReady: Boolean,
        val memberId: Long? = null,
        val isMe: Boolean = false,
        val isEntered: Boolean = true,
        val faceKey: String? = null,
        val shownGender: Gender? = null,
        /** 아바타 모습(크루런 트랙의 가장 가까운 크루원, [임시:D-성-254]). 옛 서버면 null이다. */
        val look: RunnerLook? = null
    )
}
