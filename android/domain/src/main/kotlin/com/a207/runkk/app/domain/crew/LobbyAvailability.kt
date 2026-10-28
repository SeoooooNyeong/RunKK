package com.a207.runkk.app.domain.crew

import java.time.Duration
import java.time.Instant

/**
 * 모집글의 대기실 입장 가능 여부(FR-133 "시작 전 30분 안이면 대기실 들어가기", FR-147). 서버 규칙(CrewRunPost.enterLobby,
 * 6.4: 개방 시각 ≤ now < 모집 일시, 이르면 LOBBY_NOT_OPEN, 늦거나 시작·취소면 ALREADY_STARTED)과 같게 판단한다.
 * now는 서버 시각 추정(ServerClock)이다. 서버가 대기실 열림(LOBBY_OPEN)이라 했으면 추정 시각이 조금 일러도 연다.
 */
sealed interface LobbyAvailability {
    /** 아직 안 열림. [minutesLeft]는 열릴 때까지 남은 분(올림, 1 이상). */
    data class NotYet(val minutesLeft: Long) : LobbyAvailability

    data object Open : LobbyAvailability

    /** 모집 일시가 지났거나 시작·끝·취소된 크루런. */
    data object Closed : LobbyAvailability

    companion object {
        /** 대기실 개방(운영 기준 크루런대기실개방, FR-147): 모집 일시 30분 전. */
        val OPEN_BEFORE: Duration = Duration.ofMinutes(30)

        private val ENTERABLE = setOf(CrewRunStatus.VOTING, CrewRunStatus.LOBBY_OPEN)
        private const val MINUTE_MILLIS = 60_000L

        fun of(info: PostList.CrewRunInfo, now: Instant): LobbyAvailability {
            val status = info.status
            if (status != null && status !in ENTERABLE) return Closed
            if (!now.isBefore(info.scheduledAt)) return Closed
            val opensAt = info.scheduledAt.minus(OPEN_BEFORE)
            if (status == CrewRunStatus.LOBBY_OPEN || !now.isBefore(opensAt)) return Open
            val millis = Duration.between(now, opensAt).toMillis()
            return NotYet((millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS)
        }
    }
}
