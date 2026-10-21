package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루런 대기실(서버 crew.LobbyView, FR-147). [distance]는 크루런 거리다. */
data class Lobby(val postId: Long, val participants: List<Participant>, val meetAt: Instant, val distance: Distance) {
    /** [me]는 열람자 본인 줄이다. */
    data class Participant(
        val displayName: String,
        val ready: Boolean,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
