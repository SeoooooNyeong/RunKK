package com.a207.runkk.app.ui.share

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 카카오톡 크루 초대 메시지([임시:FR-성-37], [임시:D-성-238], [임시:D-성-240]): 크루 이름, 초대 문장, 유효 기간, 「크루 가입하기」. */
@RunWith(RobolectricTestRunner::class)
class KakaoLinkSharerTest {
    private val resources = RuntimeEnvironment.getApplication().resources
    private val invite = CrewInviteMessage(
        crewName = "새벽질주",
        code = "AB12CD",
        expiresOn = "11월 21일",
        webUrl = "https://k15a207.p.ssafy.io/invite/crew/AB12CD"
    )

    @Test
    fun `공유_카드_그림은_JPEG_주소다`() {
        assertEquals("https://k15a207.p.ssafy.io/invite/card.jpg", KakaoLinkSharer.INVITE_CARD_IMAGE)
    }

    @Test
    fun `메시지에_크루_이름과_crewInvite_인자`() {
        val template = crewInviteTemplate(resources, invite)
        val button = template.buttons!!.single()

        assertEquals("새벽질주", template.content.title)
        assertEquals("런크크 크루에 초대합니다. 11월 21일까지 가입할 수 있어요.", template.content.description)
        assertEquals("크루 가입하기", button.title)
        assertEquals(mapOf("crewInvite" to "AB12CD"), button.link.androidExecutionParams)
        assertEquals(invite.webUrl, button.link.webUrl)
        assertEquals(invite.webUrl, button.link.mobileWebUrl)
    }

    @Test
    fun `실행_인자_이름은_친구_대결의_invite와_다르다`() {
        val params = crewInviteTemplate(resources, invite).buttons!!.single().link.androidExecutionParams!!

        assertFalse("invite" in params, "invite는 친구 대결 전용이다")
        assertEquals("crewInvite", KakaoLinkSharer.CREW_INVITE_PARAM)
    }

    @Test
    fun `카드_본문_링크는_초대_링크이고_실행_인자가_없다`() {
        val content = crewInviteTemplate(resources, invite).content

        assertEquals(invite.webUrl, content.link.webUrl)
        assertNull(content.link.androidExecutionParams)
        assertFalse(content.imageUrl!!.contains("/invite/crew/"), "카드 그림은 앱 링크 경로 밖에 있다")
    }

    @Test
    fun `유효_기간을_모르면_초대_문장만_쓴다`() {
        val template = crewInviteTemplate(resources, invite.copy(expiresOn = ""))

        assertEquals("런크크 크루에 초대합니다.", template.content.description)
    }

    @Test
    fun `카카오_SDK가_초기화되지_않았으면_FAILED다`() = kotlinx.coroutines.test.runTest {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()

        assertEquals(KakaoShareOutcome.FAILED, KakaoLinkSharer().shareCrewInvite(activity, invite))
    }
}
