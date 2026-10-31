package com.a207.runkk.app.ui.runimal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 평가 결과(G-11): 경로 인자만 그린다. */
@RunWith(RobolectricTestRunner::class)
class EvaluationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val shown = mutableListOf<String>()
    private var confirmed = 0

    private fun show(tier: String, validRuns: Int, runimalType: String?, description: String? = null) =
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                EvaluationScreen(
                    tier,
                    validRuns,
                    runimalType,
                    onShowType = { shown += it },
                    onConfirm = { confirmed++ },
                    runimalDescription = description
                )
            }
        }

    @Test
    fun `TC-9737 Runimal 확정 카드는 유형 이름 아래 한 줄 설명을 보인다(G-11, R2-15)`() {
        show("GOLD", 20, "COMMUTER_TURTLE", description = "같은 길을 같은 시간에, 오래 꾸준히 달려요")
        compose.onNodeWithText("출근도장 거북이").assertExists()
        compose.onNodeWithText("같은 길을 같은 시간에, 오래 꾸준히 달려요").assertExists()
    }

    @Test
    fun `TC-9737 첫 배치는 유효 러닝 3회 달성과 티어 카드만 보이고 Runimal 카드와 유형 보기가 없다`() {
        show("BRONZE", 3, null)
        compose.onNodeWithText("유효 러닝 3회 달성").assertExists()
        compose.onNodeWithText("새 평가가 나왔어요").assertExists()
        compose.onNodeWithText("브론즈").assertExists()
        compose.onNodeWithText("Runimal 확정").assertDoesNotExist()
        compose.onNodeWithText("유형 보기").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `TC-9737 재평가에 확정 유형이 있으면 Runimal 확정 카드와 유형 보기, 확인을 보인다`() {
        show("GOLD", 20, "COMMUTER_TURTLE")
        compose.onNodeWithText("유효 러닝 20회 달성").assertExists()
        compose.onNodeWithText("골드").assertExists()
        compose.onNodeWithText("Runimal 확정").assertExists()
        compose.onNodeWithText("출근도장 거북이").assertExists()
        compose.onNodeWithText("유형 보기").performClick()
        compose.onNodeWithText("확인").performClick()
        assertEquals(listOf("COMMUTER_TURTLE"), shown)
        assertEquals(1, confirmed)
    }

    @Test
    fun `TC-9737 재평가에 확정 유형이 없으면 티어만 보인다`() {
        show("SILVER", 13, null)
        compose.onNodeWithText("실버").assertExists()
        compose.onNodeWithText("Runimal 확정").assertDoesNotExist()
        compose.onNodeWithText("유형 보기").assertDoesNotExist()
        compose.onNodeWithText("확인").assertExists()
    }

    @Test
    fun `UNRANKED는 받은 값 그대로 보인다`() {
        show("UNRANKED", 3, null)
        compose.onNodeWithText("UNRANKED").assertExists()
    }

    @Test
    fun `티어 이름은 서버 Tier 이름으로 찾고 UNRANKED와 모르는 값은 없다`() {
        val names = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER").map {
            RuntimeEnvironment.getApplication().getString(EvaluationTier.of(it)!!.nameRes)
        }
        assertEquals(listOf("브론즈", "실버", "골드", "플래티넘", "마스터"), names)
        assertNull(EvaluationTier.of("UNRANKED"))
        assertNull(EvaluationTier.of("DIAMOND"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class EvaluationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reevaluationWithType() = compose.captureRunkkScreen("G11_evaluation") {
        EvaluationScreen(
            "GOLD",
            20,
            "COMMUTER_TURTLE",
            onShowType = {},
            onConfirm = {},
            runimalDescription = "같은 길을 같은 시간에, 오래 꾸준히 달려요"
        )
    }
}
