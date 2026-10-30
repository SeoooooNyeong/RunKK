package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 1차 배포 앱 셸의 공용 부품(D-250~D-254, D-267, D-277): 하단 탭 순서·잠금, 숨김 판정, 기본 아바타, 위치 동의 시트, 가이드 앵커.
 * 시험 설계 10-06 TC-9694, TC-9697, TC-9698(결과 크루 줄), TC-9701, TC-9685.
 */
@RunWith(RobolectricTestRunner::class)
class ReleaseShellTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = RuntimeEnvironment.getApplication().resources

    @Test
    fun `TC-9697 1차 배포는 미공개 기능 셋을 모두 닫고 2차 배포는 모두 연다`() {
        UnreleasedFeature.entries.forEach {
            assertFalse(ReleaseScope.of(isLockingUnreleased = true).isOpen(it))
            assertTrue(ReleaseScope.of(isLockingUnreleased = false).isOpen(it))
        }
        val crewOnly = ReleaseScope.opened(setOf(UnreleasedFeature.CREW))
        assertTrue(crewOnly.isOpen(UnreleasedFeature.CREW))
        assertFalse(crewOnly.isOpen(UnreleasedFeature.CARD_BACK))
    }

    @Test
    fun `D93 기록 카드 앞면 공유는 잠금 항목이 아니고 뒷면과 Runimal 공유만 잠근다`() {
        assertEquals(
            listOf("CREW", "CARD_BACK", "RUNIMAL_SHARE", "AVATAR_CUSTOMIZE"),
            UnreleasedFeature.entries.map { it.name }
        )
    }

    @Test
    fun `TC-9694 하단 탭은 크루·기록런·홈·랭킹·마이 순서다`() {
        assertEquals(
            listOf("크루", "기록런", "홈", "랭킹", "마이"),
            RunkkTab.entries.map { res.getString(it.labelRes) }
        )
    }

    @Test
    fun `TC-9694 잠긴 크루 탭은 잠김으로 읽히고 다른 탭은 그렇지 않으며 눌러도 고르기를 알린다`() {
        var picked: RunkkTab? = null
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomBar(selected = RunkkTab.HOME, onSelect = { picked = it }, lockedTabs = setOf(RunkkTab.CREW))
            }
        }
        compose.onNodeWithText("크루").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "잠김"))
        compose.onNodeWithText("랭킹").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("크루").performClick()
        assertEquals(RunkkTab.CREW, picked)
    }

    @Test
    fun `하단 탭은 가이드 앵커를 적는다(D-277)`() {
        val anchors = GuideAnchors()
        compose.setContent {
            CompositionLocalProvider(LocalGuideAnchors provides anchors) {
                RunkkTheme(reduceMotion = true) { RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}) }
            }
        }
        compose.waitForIdle()
        listOf(GuideStep.CREW, GuideStep.RECORD_RUN, GuideStep.RANKING, GuideStep.ME).forEach {
            assertNotNull(anchors[it], "$it 앵커")
        }
        assertEquals(null, anchors[GuideStep.FREE_RUN])
    }

    @Test
    fun `TC-9701 아바타 꾸미기가 닫혀 있으면 얼굴 키를 무시하고 표정별 기본 아바타를 그린다`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RunkkAvatar("default:BEAR", AvatarExpression.SAD, Modifier.size(40.dp))
                }
            }
        }
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "SAD", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(R.drawable.avatar_default_joy, defaultAvatarOf(AvatarExpression.JOY))
    }

    @Test
    fun `TC-9701 매칭 대기는 찾는 자세, 결과 상세는 뛰어오르는 자세 그림이다`() {
        assertEquals(R.drawable.avatar_default_search, defaultAvatarOf(AvatarExpression.SEARCH))
        assertEquals(R.drawable.avatar_default_jump, defaultAvatarOf(AvatarExpression.JUMP))
        assertEquals(
            AvatarExpression.JUMP,
            RunResultDetailState(Distance(5_000), "", Duration.ZERO).expression,
            "결과 상세 기본"
        )
    }

    @Test
    fun `TC-9701 아바타 꾸미기가 열려 있으면 기존 얼굴 그림을 쓴다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkAvatar("default:BEAR", AvatarExpression.JOY, Modifier.size(40.dp)) }
        }
        compose.onNodeWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "JOY", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TC-9685 위치 동의 시트는 디자인 문구와 닫기·동의하고 시작이다`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                LocationConsentSheet(onAgree = { picked += "agree" }, onClose = { picked += "close" })
            }
        }
        compose.onNodeWithText("위치 정보 동의가 필요해요").assertExists()
        compose.onNodeWithText("러닝을 기록하려면 위치 정보 수집에 동의해 주세요.").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("동의하고 시작").performClick()
        assertEquals(listOf("close", "agree"), picked)
    }

    @Test
    fun `TC-9698 결과 화면 크루 반영 줄은 CREW가 닫혀 있으면 그리지 않고 열리면 그린다`() {
        var scope by mutableStateOf(ReleaseScope.FIRST_RELEASE)
        val state = RunResultDetailState(Distance(5_000), "10.16 목", Duration.ofMinutes(30), crewLine = "크루 레이스 반영")
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides scope) {
                RunkkTheme(reduceMotion = true) { RunResultDetail(state) }
            }
        }
        compose.onNodeWithText("크루 레이스 반영").assertDoesNotExist()
        scope = ReleaseScope.ALL_OPEN
        compose.onNodeWithText("크루 레이스 반영").assertExists()
    }
}
