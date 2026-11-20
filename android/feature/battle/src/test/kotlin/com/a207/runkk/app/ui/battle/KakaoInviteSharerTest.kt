package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 카카오톡 초대 메시지(D-270): 제목 문구(TC-9673)와 카드 그림 주소(AN8-9), 대결 안내 문구(TC-9723). */
@RunWith(RobolectricTestRunner::class)
class KakaoInviteSharerTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `TC-9673 제목은 닉네임과 띄어 쓴 거리, 설명과 버튼은 고정 문구다`() {
        assertEquals("바람처럼님이 5 km 대결을 신청했어요", inviteShareTitle(resources, "바람처럼", Distance(5_000)))
        assertEquals("바람처럼님이 1.5 km 대결을 신청했어요", inviteShareTitle(resources, "바람처럼", Distance(1_500)))
        assertEquals("런크크 배틀런 친구 대결", resources.getString(R.string.battle_invite_share_description))
        assertEquals("대결 참가하기", resources.getString(R.string.battle_invite_share_button))
    }

    @Test
    fun `TC-9673 AN8-2 닉네임을 모르면 이름 없는 제목을 쓴다`() {
        assertEquals("친구가 5 km 대결을 신청했어요", inviteShareTitle(resources, null, Distance(5_000)))
        assertEquals("친구가 5 km 대결을 신청했어요", inviteShareTitle(resources, " ", Distance(5_000)))
    }

    @Test
    fun `TC-9723 AN8-6 언랭 안내는 D10 이름(셀프런)으로 쓴다`() {
        assertEquals("유효 러닝 3회 뒤 랜덤 대결 가능, 셀프런은 지금 가능", resources.getString(R.string.battle_error_ranked_only))
    }

    @Test
    fun `AN8-9 카드 그림은 앱 링크 경로 밖에 있다`() {
        assertEquals("https://k15a207.p.ssafy.io/invite/card.jpg", KakaoInviteSharer.INVITE_CARD_IMAGE)
        assertFalse(KakaoInviteSharer.INVITE_CARD_IMAGE.contains("/invite/battle/"))
    }
}
