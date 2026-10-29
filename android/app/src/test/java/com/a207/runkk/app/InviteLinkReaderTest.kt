package com.a207.runkk.app

import android.content.Intent
import android.net.Uri
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 초대 링크 수신(D-272): 토큰 꺼내기(TC-9674·TC-9675)와 여는 때(TC-9676). */
@RunWith(RobolectricTestRunner::class)
class InviteLinkReaderTest {
    private val reader = InviteLinkReader("kakaoabc123")
    private val token = "aB3-_xY0aB3-_xY0aB3-_x"

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
    fun `TC-9676 로그인 상태이고 홈이 맨 위일 때만 연다`() {
        assertEquals(token, inviteToOpen(token, loggedIn = true, top = Routes.Key(Routes.HOME)))
        assertNull(inviteToOpen(token, loggedIn = false, top = Routes.Key(Routes.LOGIN)), "로그인 전이면 기다린다")
        assertNull(inviteToOpen(token, loggedIn = true, top = Routes.Key(Routes.SIGN_UP)), "가입 중이면 기다린다")
        assertNull(inviteToOpen(token, loggedIn = true, top = Routes.Key(Routes.RUN)), "측정 중이면 기다린다")
        assertNull(inviteToOpen(null, loggedIn = true, top = Routes.Key(Routes.HOME)))
    }

    @Test
    fun `D-275 앱 키가 없으면 카카오 SDK를 초기화하지 않는다`() {
        assertFalse(KakaoSdkStartup.init(RuntimeEnvironment.getApplication(), ""))
    }
}
