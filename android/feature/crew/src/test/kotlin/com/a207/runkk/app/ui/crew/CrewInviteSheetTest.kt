package com.a207.runkk.app.ui.crew

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.CREW_INVITE_PARAM
import com.a207.runkk.app.core.navigation.CREW_INVITE_PATH
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.ui.share.CrewInviteMessage
import com.a207.runkk.app.ui.share.KakaoLinkSharer
import com.a207.runkk.app.ui.share.KakaoShareOutcome
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 초대 창([임시:FR-성-37], [임시:D-성-241], 실기기 수정 요청 ⑧): 만료일을 보이고 재발급 단추는 없다. 세 동작(카카오톡으로 보내기,
 * 초대 링크 복사, 다른 앱으로 공유)과 카카오톡 실패 안내.
 */
@RunWith(RobolectricTestRunner::class)
class CrewInviteSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()
    private val context = RuntimeEnvironment.getApplication()
    private val calls = mutableListOf<String>()

    /** 어느 시간대에서도 11월 21일인 시각. */
    private val expiresAt = Instant.parse("2026-11-21T12:00:00Z")
    private val actions = CrewHomeActions(
        onInviteClose = { calls += "close" },
        onInviteCopied = { calls += "copied" },
        onInviteKakao = { calls += "kakao" },
        onInviteLinkCopy = { calls += "link" },
        onInviteSystemShare = { calls += "share" }
    )

    private fun show(expiresAt: Instant? = this.expiresAt) = compose.setContent {
        RunkkTheme(reduceMotion = true) { CrewInviteSheetContent("K7P2QX", expiresAt, "새벽질주", actions) }
    }

    private fun clipboardText(): String? =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text
            ?.toString()

    @Test
    fun `만료일을_보인다`() {
        show()

        compose.onNodeWithText("초대 코드, 11월 21일까지").assertExists()
        compose.onNodeWithText("K7P2QX").assertExists()
    }

    @Test
    fun `만료일을_모르면_초대_코드라고만_적는다`() {
        show(expiresAt = null)

        compose.onNodeWithText("초대 코드").assertExists()
        compose.onNodeWithText("까지", substring = true).assertDoesNotExist()
    }

    @Test
    fun `재발급_단추가_없다`() {
        show()

        compose.onNodeWithText("재발급", substring = true).assertDoesNotExist()
        compose.onNodeWithText("새로 발급", substring = true).assertDoesNotExist()
        compose.onNodeWithText("복사").assertExists()
        compose.onNodeWithText("카카오톡으로 보내기").assertExists()
        compose.onNodeWithText("누르면 바로 가입 확인 화면이 열려요").assertExists()
        compose.onNodeWithText("초대 링크 복사").assertExists()
        compose.onNodeWithText("문자나 메신저에 붙여 넣기").assertExists()
        compose.onNodeWithText("다른 앱으로 공유").assertExists()
        compose.onNodeWithText("공유 화면 열기").assertExists()
        compose.onNodeWithText("닫기").assertExists()
    }

    /** 설계서 이름은 「링크_복사가_/invite/crew/CODE를_복사한다」다. JVM 메소드 이름에 `/`를 쓸 수 없어 밑줄로 바꿨다. */
    @Test
    fun `링크_복사가_invite_crew_CODE를_복사한다`() {
        show()

        compose.onNodeWithText("초대 링크 복사").performClick()
        compose.waitForIdle()

        assertEquals("https://k15a207.p.ssafy.io/invite/crew/K7P2QX", clipboardText())
        assertEquals(listOf("link"), calls)
    }

    @Test
    fun `복사는_코드만_복사한다`() {
        show()

        compose.onNodeWithText("복사").performClick()
        compose.waitForIdle()

        assertEquals("K7P2QX", clipboardText())
        assertEquals(listOf("copied"), calls)
    }

    @Test
    fun `세_동작과_닫기는_각자의_입력을_부른다`() {
        show()

        compose.onNodeWithText("카카오톡으로 보내기").performClick()
        compose.onNodeWithText("다른 앱으로 공유").performClick()
        compose.onNodeWithText("닫기").performClick()

        assertEquals(listOf("kakao", "share", "close"), calls)
    }

    @Test
    fun `카카오_실패시_안내를_띄우고_창을_둔다`() = runTest {
        val vm = CrewHomeViewModel(FakeCrewRepository(), ServerClock(MutableClock()), FakeCelebrationSharer(), { _, _ ->
            error("쓰지 않는다")
        })
        vm.bind(Routes.CrewHome(11))
        vm.onInviteOpen()

        vm.events.test {
            vm.onInviteKakaoResult(KakaoShareOutcome.KAKAO_TALK)
            vm.onInviteKakaoResult(KakaoShareOutcome.WEB)
            expectNoEvents()

            vm.onInviteKakaoResult(KakaoShareOutcome.FAILED)
            assertEquals(CrewHomeEvent.InviteKakaoFailed, awaitItem())
        }
        assertTrue(vm.state.value.isInviteOpen, "창은 그대로 둔다")
        assertNull(vm.state.value.error)
        assertEquals("카카오톡을 열 수 없어요", context.getString(R.string.crew_invite_kakao_failed))
    }

    @Test
    fun `링크를_복사하면_창을_닫고_안내한다`() {
        val vm = CrewHomeViewModel(FakeCrewRepository(), ServerClock(MutableClock()), FakeCelebrationSharer(), { _, _ ->
            error("쓰지 않는다")
        })
        vm.bind(Routes.CrewHome(11))
        vm.onInviteOpen()

        vm.onInviteLinkCopied()

        assertEquals(false, vm.state.value.isInviteOpen)
        assertEquals(uiText(R.string.crew_invite_link_copied), vm.state.value.message)
    }

    @Test
    fun `초대_메시지는_지금_초대_코드의_링크와_만료일을_담는다`() {
        val seoul = ZoneId.of("Asia/Seoul")
        val detail = crewDetail(role = CrewRole.MEMBER).copy(inviteCodeExpiresAt = expiresAt)

        assertEquals(
            CrewInviteMessage(detail.name, "K7P2QX", "11월 21일", "https://k15a207.p.ssafy.io/invite/crew/K7P2QX"),
            crewInviteMessage(context.resources, detail, seoul)
        )
        assertEquals(
            "",
            crewInviteMessage(context.resources, detail.copy(inviteCodeExpiresAt = null), seoul)?.expiresOn
        )
        assertNull(crewInviteMessage(context.resources, crewDetail(role = null)), "초대 코드를 못 받았으면 만들지 않는다")
        assertNull(crewInviteMessage(context.resources, null))
    }

    @Test
    fun `초대_경로와_카카오_실행_인자는_읽는_쪽과_같다`() {
        assertEquals("/invite/crew/", CREW_INVITE_PATH)
        assertEquals(CREW_INVITE_PARAM, KakaoLinkSharer.CREW_INVITE_PARAM, "ui.share와 core.navigation의 인자 이름")
        assertEquals("crewInvite", CREW_INVITE_PARAM)
    }

    @Test
    fun `다른_앱으로_보내는_글은_크루_이름과_유효_기간과_링크를_담는다`() {
        val invite = CrewInviteMessage("새벽질주", "K7P2QX", "11월 21일", "https://k15a207.p.ssafy.io/invite/crew/K7P2QX")

        assertEquals(
            "새벽질주 크루에서 초대했어요. 11월 21일까지 아래 링크로 가입할 수 있어요.\nhttps://k15a207.p.ssafy.io/invite/crew/K7P2QX",
            crewInviteShareText(context.resources, invite)
        )
        assertEquals(
            "새벽질주 크루에서 초대했어요. 아래 링크를 누르면 가입 확인 화면이 열려요.\nhttps://k15a207.p.ssafy.io/invite/crew/K7P2QX",
            crewInviteShareText(context.resources, invite.copy(expiresOn = ""))
        )
    }

    @Test
    fun inviteSheet() = compose.captureRunkkScreen("S306_crew_invite_sheet", isDarkTheme = false) {
        CrewInviteSheetContent("K7P2QX", expiresAt, "새벽질주", CrewHomeActions())
    }
}
