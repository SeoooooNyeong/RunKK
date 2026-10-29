package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.Instant

/**
 * 대기 전략(D-269). MatchViewModel의 대기 → 성사 → 자동 출발 흐름은 하나이고, 대기를 여는 요청·끝 판정·떠날 때 정리만 대기 종류마다
 * 다르다. [open]은 대기를 연다(실시간 채널을 연결한 뒤에 부른다), [endOf]는 이 대기를 끝내는 메시지면 그 끝, [abandon]은 화면을 떠날 때
 * 서버 정리, [deadline]은 로컬 만료 시각(없으면 null)이다.
 */
sealed interface MatchTicket {
    val opponentKind: OpponentKind
    val deadline: Instant?

    suspend fun open(): Outcome<Unit>

    fun endOf(m: ServerMessage): WaitEnd?

    fun abandon()
}

/** 랜덤 매칭(FR-080). 매칭 요청은 조정자가 applicationScope에서 보내 이탈 취소가 요청보다 먼저 가지 않는다. */
class RandomTicket(private val distance: Distance, private val coordinator: BattleRunCoordinator) : MatchTicket {
    override val opponentKind: OpponentKind = OpponentKind.RANDOM
    override val deadline: Instant? = null

    override suspend fun open(): Outcome<Unit> = when (val accepted = coordinator.requestMatch(distance)) {
        is Outcome.Success -> Outcome.Success(Unit)
        is Outcome.Failure -> accepted
    }

    override fun endOf(m: ServerMessage): WaitEnd? = when (m) {
        is MatchFound -> WaitEnd.Found(m)
        is MatchFailed -> WaitEnd.Failed(m)
        else -> null
    }

    override fun abandon() = coordinator.abandonMatching()
}

/**
 * 친구 대결 초대(FR-227, FR-228). 초대를 만들고 [invite]로 들고 있다. 성사(MatchFound), 거절·만료 알림 가운데 이 초대의 것만 끝으로
 * 보고, 친구 대결은 고스트로 넘어가지 않으므로 MatchFailed는 보지 않는다. 떠나면 조정자가 초대를 취소한다(D-271).
 */
class FriendInviteTicket(
    private val distance: Distance,
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator
) : MatchTicket {
    override val opponentKind: OpponentKind = OpponentKind.FRIEND

    /** 만든 초대. 만들기 전이면 null이다. */
    var invite: FriendInvite? = null
        private set

    override val deadline: Instant? get() = invite?.expiresAt

    override suspend fun open(): Outcome<Unit> = when (val created = battles.createFriendInvite(distance)) {
        is Outcome.Success -> {
            invite = created.value
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> created
    }

    override fun endOf(m: ServerMessage): WaitEnd? = when (m) {
        is MatchFound -> WaitEnd.Found(m)
        is FriendInviteDeclined -> if (m.inviteId == invite?.inviteId) WaitEnd.Declined else null
        is FriendInviteExpired -> if (m.inviteId == invite?.inviteId) WaitEnd.Expired else null
        else -> null
    }

    override fun abandon() {
        invite?.let { coordinator.cancelInvite(it.inviteId) }
    }
}

/** 받은 초대에 참가(FR-229). 참가 응답 뒤 서버가 보내는 MatchFound만 끝이다. 떠나도 정리할 것이 없다. */
class FriendJoinTicket(private val token: String, private val battles: BattleRepository) : MatchTicket {
    override val opponentKind: OpponentKind = OpponentKind.FRIEND
    override val deadline: Instant? = null

    /** 참가가 받아들여진 대결 ID. 참가 전이면 null이다. */
    var battleId: Long? = null
        private set

    override suspend fun open(): Outcome<Unit> = when (val accepted = battles.acceptFriendInvite(token)) {
        is Outcome.Success -> {
            battleId = accepted.value
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> accepted
    }

    override fun endOf(m: ServerMessage): WaitEnd? = (m as? MatchFound)?.let { WaitEnd.Found(it) }

    override fun abandon() = Unit
}

/** 대기가 끝난 까닭(D-269). */
sealed interface WaitEnd {
    data class Found(val match: MatchFound) : WaitEnd

    data class Failed(val failed: MatchFailed) : WaitEnd

    /** 친구가 초대를 거절했다. */
    data object Declined : WaitEnd

    /** 초대가 만료됐다(서버 알림 또는 로컬 만료 시각). */
    data object Expired : WaitEnd

    /** 대기를 여는 요청이 거부·실패했다. */
    data class Refused(val error: AppError) : WaitEnd

    /** 연결 실패(error) 또는 결과 없이 채널이 끝남(null). */
    data class Lost(val error: AppError?) : WaitEnd
}
