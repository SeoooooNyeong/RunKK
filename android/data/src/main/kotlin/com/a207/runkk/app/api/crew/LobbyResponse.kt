package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.RunnerLookJson
import kotlinx.serialization.Serializable

/** 서버 CrewDtos.LobbyResponse. 모집 일시는 scheduledAt, 크루런 거리는 distanceMeters(null이면 제한 없음, W6)다. */
@Serializable
internal data class LobbyResponse(
    val postId: Long,
    val participants: List<Participant> = emptyList(),
    val scheduledAt: String,
    val distanceMeters: Long? = null
) {
    /** me는 열람자 본인 줄이다. */
    @Serializable
    data class Participant(
        val memberId: Long? = null,
        val displayName: String? = null,
        val ready: Boolean = false,
        val me: Boolean = false,
        /** NOT_ENTERED | ENTERING | READY(서버 crew.Readiness). 옛 서버면 null. */
        val readiness: String? = null,
        /** 아바타 얼굴 키. 얼굴을 고르지 않았거나 옛 서버면 null(계약 crewrun-sum v1.2). */
        val faceKey: String? = null,
        /** 표시 성별 MALE | FEMALE(D-327). 옛 서버면 null. */
        val shownGender: String? = null,
        /** 아바타 모습([임시:D-성-254]). 옛 서버면 null. */
        val look: RunnerLookJson? = null
    )
}
