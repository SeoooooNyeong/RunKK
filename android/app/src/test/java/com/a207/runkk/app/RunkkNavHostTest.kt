package com.a207.runkk.app

import com.a207.runkk.app.core.navigation.Routes
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 크루 초대 링크를 여는 때와 경로([임시:FR-성-38], [임시:D-성-242], [임시:D-성-244] ④): 로그인 전이면 로그인 뒤 홈이 맨 위일 때 가입
 * 확인을 열고, 로그아웃하면 버린다. 친구 대결 초대와 수명·경로가 다르다.
 */
class RunkkNavHostTest {
    private val received = Instant.parse("2026-11-14T10:00:00Z")
    private val crew = PendingInvite.of(InviteLink.Crew("AB12CD"), received)
    private val home = Routes.Key(Routes.HOME)

    @Test
    fun `크루_초대는_로그인_뒤_홈이_맨_위일_때_연다`() {
        assertNull(inviteToOpen(crew, loggedIn = false, top = Routes.Key(Routes.LOGIN), now = received), "로그인 전이면 기다린다")
        assertNull(inviteToOpen(crew, loggedIn = true, top = Routes.Key(Routes.SIGN_UP), now = received), "가입 중이면 기다린다")
        assertNull(inviteToOpen(crew, loggedIn = true, top = Routes.Key(Routes.RUN), now = received), "측정 중이면 기다린다")
        assertEquals(crew, pendingAfterSession(crew, wasLoggedIn = false, loggedIn = true), "로그인 전에 받은 초대는 남긴다")

        assertEquals("AB12CD", inviteToOpen(crew, loggedIn = true, top = home, now = received))
        assertEquals(Routes.CrewInviteJoin("AB12CD"), inviteRoute(crew))
    }

    @Test
    fun `로그아웃하면_보류_크루초대를_버린다`() {
        assertNull(pendingAfterSession(crew, wasLoggedIn = true, loggedIn = false))
        assertEquals(crew, pendingAfterSession(crew, wasLoggedIn = true, loggedIn = true))
    }

    @Test
    fun `친구_대결_초대는_참가_확인으로_연다`() {
        val battle = PendingInvite.of(InviteLink.FriendBattle("tok-1"), received)

        assertEquals(InviteKind.FRIEND_BATTLE, battle.kind)
        assertEquals(Routes.FriendInviteJoin("tok-1"), inviteRoute(battle))
        assertEquals(battle, PendingInvite("tok-1", received), "종류를 안 주면 친구 대결 초대다")
    }

    @Test
    fun `크루_초대는_30분_동안_남고_대결_초대는_5분이다`() {
        val battle = PendingInvite("tok-1", received)
        val afterFive = received.plus(Duration.ofMinutes(5))
        val almostThirty = received.plus(Duration.ofMinutes(30)).minusMillis(1)
        val afterThirty = received.plus(Duration.ofMinutes(30))

        assertFalse(battle.isFresh(afterFive))
        assertTrue(crew.isFresh(afterFive), "로그인·가입을 마칠 동안 남긴다")
        assertEquals("AB12CD", inviteToOpen(crew, loggedIn = true, top = home, now = almostThirty))
        assertFalse(crew.isFresh(afterThirty))
        assertNull(inviteToOpen(crew, loggedIn = true, top = home, now = afterThirty))
    }
}
