@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 대기 전략 세 구현(TC-9661~TC-9663). */
class MatchTicketTest {
    private val failed = MatchFailed("MATCH_FAILED", "")
    private val progress =
        OpponentProgress(9, "x", Distance(1), Duration.ZERO, 0, OpponentProgress.Connection.CONNECTED)

    @Test
    fun `TC-9661 RandomTicket - 요청, 성사·실패가 끝, 이탈은 대기열 취소, 기한 없음`() = runTest {
        val w = world()
        val ticket = RandomTicket(Distance(3_000), w.coordinator)
        assertEquals(Outcome.Success(Unit), ticket.open())
        assertEquals(listOf("match:3000"), w.battles.calls)
        assertEquals(WaitEnd.Found(matchFound()), ticket.endOf(matchFound()))
        assertEquals(WaitEnd.Failed(failed), ticket.endOf(failed))
        assertNull(ticket.endOf(progress))
        assertNull(ticket.deadline)
        assertEquals(OpponentKind.RANDOM, ticket.opponentKind)
        ticket.abandon()
        runCurrent()
        assertTrue("cancel" in w.battles.calls)
    }

    @Test
    fun `TC-9662 FriendInviteTicket - 초대 만들기, 이 초대의 거절·만료·성사만 끝, MatchFailed는 무시, 기한은 만료 시각`() = runTest {
        val w = world()
        val ticket = FriendInviteTicket(Distance(5_000), w.coordinator)
        assertNull(ticket.deadline)
        ticket.open()
        assertEquals(friendInvite().expiresAt, ticket.deadline)
        assertEquals(WaitEnd.Found(matchFound()), ticket.endOf(matchFound()))
        assertEquals(WaitEnd.Declined, ticket.endOf(FriendInviteDeclined(41)))
        assertEquals(WaitEnd.Expired, ticket.endOf(FriendInviteExpired(41)))
        assertNull(ticket.endOf(FriendInviteExpired(42)))
        assertNull(ticket.endOf(failed), "친구 대결은 고스트로 넘어가지 않는다")
        assertNull(ticket.endOf(progress))
        assertEquals(OpponentKind.FRIEND, ticket.opponentKind)
        ticket.abandon()
        runCurrent()
        assertTrue("cancelInvite:41" in w.battles.calls)
    }

    @Test
    fun `TC-9663 FriendJoinTicket - 참가, MatchFound만 끝, 이탈은 아무것도 안 함`() = runTest {
        val w = world()
        val ticket = FriendJoinTicket("tok", w.battles)
        ticket.open()
        assertEquals(9L, ticket.battleId)
        assertEquals(WaitEnd.Found(matchFound()), ticket.endOf(matchFound()))
        assertNull(ticket.endOf(FriendInviteExpired(41)))
        assertNull(ticket.deadline)
        ticket.abandon()
        runCurrent()
        assertEquals(listOf("accept:tok"), w.battles.calls)
    }
}
