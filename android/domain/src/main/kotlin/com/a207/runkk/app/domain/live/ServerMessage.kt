package com.a207.runkk.app.domain.live

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant

/**
 * 서버 → 앱 실시간 메시지(5.7.2). 서버는 type() 이름으로 직렬화하고(D-06), data.live.ServerMessageDecoder가
 * type 필드를 보고 아래 11종 가운데 하나로 해독한다. 항목 이름은 서버 battle.live·crew.live record와 같다(H-17 계약 시험).
 */
sealed interface ServerMessage

/** 매칭 성사(BattleStartService.announceMatch). 고스트도 랜덤과 같은 항목만 가진다(Q-02). */
data class MatchFound(
    val battleId: Long,
    val opponentName: String,
    val opponentTier: String,
    val distance: Distance,
    val myBest: Duration?,
    val opponentBest: Duration?,
    val animationSeconds: Int
) : ServerMessage

/**
 * 매칭 실패(MatchmakingService가 대결을 만들지 못함). 받으면 서버가 이미 대기열에서 뺀 상태이므로 앱은 취소를 보내지 않고
 * 대기 화면을 닫는다. [code]는 사유 코드(MATCH_FAILED), [message]는 서버 안내 문구다.
 */
data class MatchFailed(val code: String, val message: String) : ServerMessage

/** 대결 시작 예약. opponentTrack이 있으면 기록 상대(고스트)라 앱이 보간한다(D-05). */
data class BattleStart(val battleId: Long, val startAt: Instant, val opponentTrack: TrackTable?) : ServerMessage

/** 상대 진행값(LiveBattleService.onProgress). gapMeters는 받는 사람 기준(앞서면 양수). */
data class OpponentProgress(
    val battleId: Long,
    val opponentName: String,
    val opponentDistance: Distance,
    val opponentElapsed: Duration,
    val gapMeters: Int,
    val connection: Connection
) : ServerMessage {
    enum class Connection {
        CONNECTED,
        CHECKING
    }
}

/** 추월·역전 안내(10m 넘는 차로 선두가 바뀔 때, FR-188). */
data class BattleGuidance(val battleId: Long, val kind: Kind, val gapMeters: Int) : ServerMessage {
    enum class Kind {
        OVERTOOK,
        OVERTAKEN
    }
}

/** 한 사람이 도착해 종료 카운트다운이 시작됐다(FR-107). */
data class CountdownStarted(val battleId: Long, val arrivedName: String, val endsAt: Instant) : ServerMessage

/** 대결 종료. */
data class BattleEnded(val battleId: Long, val status: Status) : ServerMessage {
    enum class Status {
        RESULT_PENDING,
        FINISHED,
        OPPONENT_FORFEITED
    }
}

/** 크루런 출발(모집 일시, 3초 카운트다운 뒤 시작, FR-148). */
data class CrewRunStart(val postId: Long, val startAt: Instant, val participantNames: List<String>) : ServerMessage

/** 크루런 취소. */
data class CrewRunCancelled(val postId: Long, val reason: String) : ServerMessage

/**
 * 크루런 중계: 참가자마다 표시명·누적 거리·순위·연결 상태(FR-149). [Entry.memberId]는 참가자 회원 ID이고,
 * [Entry.isMe]는 받는 사람 본인 줄이다(data.live가 접근 토큰의 회원 ID와 memberId를 비교해 채운다).
 */
data class CrewRunRelay(val postId: Long, val entries: List<Entry>) : ServerMessage {
    data class Entry(
        val name: String,
        val distance: Distance,
        val rank: Int?,
        val connection: Connection,
        val memberId: Long? = null,
        val isMe: Boolean = false
    )

    enum class Connection {
        CONNECTED,
        CHECKING,
        QUIT,
        ARRIVED
    }
}

/** 크루런 종료. */
data class CrewRunEnded(val postId: Long) : ServerMessage
