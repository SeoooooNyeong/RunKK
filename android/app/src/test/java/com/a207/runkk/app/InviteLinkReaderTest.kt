package com.a207.runkk.app

import android.content.Intent
import android.net.Uri
import com.a207.runkk.app.core.navigation.Routes
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 초대 링크 수신(D-272): 토큰 꺼내기(TC-9674·TC-9675)와 여는 때(TC-9676). */
@RunWith(RobolectricTestRunner::class)
class InviteLinkReaderTest {
    private val reader = InviteLinkReader("kakaoabc123")
    private val token = "aB3-_xY0aB3-_xY0aB3-_x"
    private val received = Instant.parse("2026-10-30T10:00:00Z")

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    @Test
    fun `TC-9674 앱 링크와 카카오톡 실행 링크에서 같은 토큰을 꺼낸다`() {
        assertEquals(token, reader.read(view("https://k15a207.p.ssafy.io/invite/battle/$token")))
        assertEquals(token, reader.read(view("kakaoabc123://kakaolink?invite=$token")))
    }

    @Test
    fun `TC-9675 다른 호스트·경로·빈 토큰·허용 밖 글자는 버리고 64자까지 받는다`() {
        assertNull(reader.read(view("https://evil.example.com/invite/battle/$token")))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/other/$token")))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/battle/")))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/battle/a%20b")))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/battle/a/b")))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/battle/card.png")), "초대 카드 그림 주소는 토큰이 아니다")
        assertEquals("a".repeat(64), reader.read(view("https://k15a207.p.ssafy.io/invite/battle/" + "a".repeat(64))))
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/battle/" + "a".repeat(65))))
    }

    @Test
    fun `다른 카카오 앱 키·다른 호스트·http·VIEW가 아닌 인텐트는 버린다`() {
        assertNull(reader.read(view("kakaozzz://kakaolink?invite=$token")))
        assertNull(reader.read(view("kakaoabc123://oauth?invite=$token")))
        assertNull(reader.read(view("http://k15a207.p.ssafy.io/invite/battle/$token")))
        assertNull(
            reader.read(
                Intent(Intent.ACTION_MAIN).setData(Uri.parse("https://k15a207.p.ssafy.io/invite/battle/$token"))
            )
        )
        assertNull(reader.read(null))
        assertNull(InviteLinkReader("").read(view("kakao://kakaolink?invite=$token")), "앱 키가 없으면 카카오 링크를 받지 않는다")
    }

    @Test
    fun `크루_https_링크는_Crew(code)`() {
        assertEquals(InviteLink.Crew("AB12CD"), reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/AB12CD")))
        assertEquals(
            InviteLink.Crew("AB12CD"),
            reader.readNewLink(view("https://K15A207.p.ssafy.io/invite/crew/AB12CD"))
        )
    }

    @Test
    fun `크루_링크는_대결_토큰으로_안_읽힌다`() {
        assertNull(reader.read(view("https://k15a207.p.ssafy.io/invite/crew/AB12CD")))
        assertNull(reader.readNew(view("https://k15a207.p.ssafy.io/invite/crew/AB12CD")))
        assertNull(reader.read(view("kakaoabc123://kakaolink?crewInvite=AB12CD")))
    }

    @Test
    fun `대결_링크는_그대로`() {
        assertEquals(
            InviteLink.FriendBattle(token),
            reader.readLink(view("https://k15a207.p.ssafy.io/invite/battle/$token"))
        )
        assertEquals(InviteLink.FriendBattle(token), reader.readLink(view("kakaoabc123://kakaolink?invite=$token")))
        // 6자 영숫자 토큰도 대결 경로·인자로 오면 대결 초대다(경로와 인자 이름으로만 가른다).
        assertEquals(
            InviteLink.FriendBattle("AB12CD"),
            reader.readLink(view("https://k15a207.p.ssafy.io/invite/battle/AB12CD"))
        )
        assertEquals(InviteLink.FriendBattle("AB12CD"), reader.readLink(view("kakaoabc123://kakaolink?invite=AB12CD")))
    }

    @Test
    fun `소문자_코드는_대문자로`() {
        assertEquals(InviteLink.Crew("AB12CD"), reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/ab12cd")))
        assertEquals(InviteLink.Crew("AB12CD"), reader.readLink(view("kakaoabc123://kakaolink?crewInvite=aB12cd")))
    }

    @Test
    fun `6자가_아니면_버린다`() {
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/AB12C")))
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/AB12CDE")))
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/")))
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/AB12-D")), "대문자·숫자만 받는다")
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/AB12CD/x")))
        assertNull(reader.readLink(view("https://k15a207.p.ssafy.io/invite/crew/index.html")), "스토어 안내 페이지 주소")
        assertNull(reader.readLink(view("https://evil.example.com/invite/crew/AB12CD")))
        assertNull(reader.readLink(view("http://k15a207.p.ssafy.io/invite/crew/AB12CD")))
        assertNull(reader.readLink(view("kakaoabc123://kakaolink?crewInvite=AB12")))
    }

    @Test
    fun `카카오_실행링크_crewInvite`() {
        assertEquals(InviteLink.Crew("AB12CD"), reader.readLink(view("kakaoabc123://kakaolink?crewInvite=AB12CD")))
        assertNull(reader.readLink(view("kakaozzz://kakaolink?crewInvite=AB12CD")), "다른 카카오 앱 키")
        assertNull(reader.readLink(view("kakaoabc123://oauth?crewInvite=AB12CD")))
        assertNull(
            InviteLinkReader("").readLink(view("kakao://kakaolink?crewInvite=AB12CD")),
            "앱 키가 없으면 카카오 링크를 받지 않는다"
        )
        val fromHistory = Intent(view("kakaoabc123://kakaolink?crewInvite=AB12CD"))
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(reader.readNewLink(fromHistory), "최근 앱에서 다시 연 실행은 읽지 않는다")
    }

    @Test
    fun `TC-9676 로그인 상태이고 홈이 맨 위일 때만 연다`() {
        val pending = PendingInvite(token, received)
        assertEquals(token, inviteToOpen(pending, loggedIn = true, top = Routes.Key(Routes.HOME), now = received))
        assertNull(inviteToOpen(pending, false, Routes.Key(Routes.LOGIN), received), "로그인 전이면 기다린다")
        assertNull(inviteToOpen(pending, true, Routes.Key(Routes.SIGN_UP), received), "가입 중이면 기다린다")
        assertNull(inviteToOpen(pending, true, Routes.Key(Routes.RUN), received), "측정 중이면 기다린다")
        assertNull(inviteToOpen(null, loggedIn = true, top = Routes.Key(Routes.HOME), now = received))
    }

    @Test
    fun `AN8-8 받은 지 5분이 지난 보류 초대는 열지 않고 버린다`() {
        val pending = PendingInvite(token, received)
        val almost = received.plus(Duration.ofMinutes(5)).minusMillis(1)
        val expired = received.plus(Duration.ofMinutes(5))
        assertTrue(pending.isFresh(almost))
        assertEquals(token, inviteToOpen(pending, loggedIn = true, top = Routes.Key(Routes.HOME), now = almost))
        assertFalse(pending.isFresh(expired))
        assertNull(inviteToOpen(pending, loggedIn = true, top = Routes.Key(Routes.HOME), now = expired))
    }

    @Test
    fun `AN8-8 로그아웃하면 보류 초대를 지우고 로그인 전에 받은 초대는 로그인 뒤까지 남긴다`() {
        val pending = PendingInvite(token, received)
        assertNull(pendingAfterSession(pending, wasLoggedIn = true, loggedIn = false), "로그아웃")
        assertEquals(pending, pendingAfterSession(pending, wasLoggedIn = false, loggedIn = true), "로그인")
        assertEquals(pending, pendingAfterSession(pending, wasLoggedIn = false, loggedIn = false), "로그인 전")
        assertEquals(pending, pendingAfterSession(pending, wasLoggedIn = true, loggedIn = true))
    }

    @Test
    fun `AN8-7 최근 앱에서 다시 연 인텐트는 읽지 않고, 처리한 링크는 인텐트에서 비운다`() {
        val link = view("https://k15a207.p.ssafy.io/invite/battle/$token")
        assertEquals(token, reader.readNew(link))
        val fromHistory = Intent(link).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(reader.readNew(fromHistory))
        val consumed = InviteLinkReader.consumed(link)
        assertNull(consumed.data)
        assertNull(reader.read(consumed))
        assertEquals(Intent.ACTION_VIEW, consumed.action)
    }

    @Test
    fun `D-275 앱 키가 없으면 카카오 SDK를 초기화하지 않는다`() {
        assertFalse(KakaoSdkStartup.init(RuntimeEnvironment.getApplication(), ""))
    }
}
