package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunnerLook
import java.time.Instant
import java.time.LocalDate

/** 크루런 대기실(서버 crew.LobbyView, FR-147). [distance]는 크루런 거리이고 null이면 제한 없음이다(W6). */
data class Lobby(val postId: Long, val participants: List<Participant>, val meetAt: Instant, val distance: Distance?) {
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
