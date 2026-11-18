package com.a207.runkk.app.ui.runimal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.runimal.TierChange
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 티어 재평가(G-11 v2, 배치고사 완료 C-16 구성): 경로 인자만 그린다. */
@RunWith(RobolectricTestRunner::class)
class EvaluationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val shown = mutableListOf<String>()
    private var confirmed = 0

    private fun show(
        tier: String,
        validRuns: Int,
        runimalType: String?,
        description: String? = null,
        previousTier: String? = null,
        averageScore: Int? = null
    ) = compose.setContent {
        RunkkTheme(reduceMotion = true) {
            EvaluationScreen(
                tier,
                validRuns,
                runimalType,
                onShowType = { shown += it },
                onConfirm = { confirmed++ },
                runimalDescription = description,
                previousTier = previousTier,
                averageScore = averageScore
            )
        }
    }

    @Test
    fun `TC-9737 Runimal 확정 카드는 유형 이름 아래 한 줄 설명을 보인다(G-11, R2-15)`() {
        show("GOLD", 20, "COMMUTER_TURTLE", description = "같은 길을 같은 시간에, 오래 꾸준히 달려요")
        compose.onNodeWithText("출근도장 거북이").performScrollTo().assertExists()
        compose.onNodeWithText("같은 길을 같은 시간에, 오래 꾸준히 달려요").assertExists()
    }

    @Test
    fun `티어 재평가는 C-16 구성으로 제목·유지 문장·n번째 유효 러닝·직전·이번·다음 평가 행을 보인다(G-11 v2)`() {
        show("GOLD", 20, "COMMUTER_TURTLE", previousTier = "GOLD", averageScore = 37)
        compose.onNodeWithText("티어 재평가").assertExists()
        compose.onNodeWithText("골드 티어를 지켰어요").assertExists()
        compose.onNodeWithText("20번째 유효 러닝").performScrollTo().assertExists()
        compose.onNodeWithText("최근 10회 평균 37점").assertExists()
        compose.onNodeWithText("직전 티어").assertExists()
        compose.onNodeWithText("이번 평가").assertExists()
        compose.onNodeWithText("골드 · 유지").assertExists()
        compose.onNodeWithText("다음 평가").assertExists()
        compose.onNodeWithText("30번째 유효 러닝").assertExists()
    }

    @Test
    fun `직전 티어로 승급·강등 문장을 고르고 직전 티어가 없으면 이번 티어만 말한다`() {
        show("GOLD", 30, null, previousTier = "SILVER")
        compose.onNodeWithText("골드로 올라갔어요").assertExists()
        compose.onNodeWithText("골드 · 승급").performScrollTo().assertExists()
        compose.onNodeWithText("실버").assertExists()
    }

    @Test
    fun `강등이면 티어가 됐어요`() {
        show("SILVER", 30, null, previousTier = "PLATINUM")
        compose.onNodeWithText("실버 티어가 됐어요").assertExists()
        compose.onNodeWithText("실버 · 강등").performScrollTo().assertExists()
    }

    @Test
    fun `직전 티어를 모르면(과거 기록·옛 서버) 티어예요 문장이고 직전 티어 행과 평균 점수가 없다`() {
        show("PLATINUM", 10, null)
        compose.onNodeWithText("플래티넘 티어예요").assertExists()
        compose.onNodeWithText("직전 티어").assertDoesNotExist()
        compose.onNodeWithText("최근 10회 평균", substring = true).assertDoesNotExist()
        compose.onNodeWithText("20번째 유효 러닝").performScrollTo().assertExists()
    }

    @Test
    fun `TC-9737 이번에 Runimal이 확정되면 Runimal 카드와 Runimal 유형 보기, 확인을 보인다`() {
        show("GOLD", 20, "COMMUTER_TURTLE", previousTier = "SILVER")
        compose.onNodeWithText("Runimal 확정").performScrollTo().assertExists()
        compose.onNodeWithText("출근도장 거북이").assertExists()
        compose.onNodeWithText("Runimal 유형 보기").performClick()
        compose.onNodeWithText("확인").performClick()
        assertEquals(listOf("COMMUTER_TURTLE"), shown)
        assertEquals(1, confirmed)
    }

    @Test
    fun `TC-9737 Runimal이 확정되지 않았으면 Runimal 카드와 유형 보기가 없다`() {
        show("SILVER", 13, null, previousTier = "SILVER")
        compose.onNodeWithText("Runimal 확정").assertDoesNotExist()
        compose.onNodeWithText("Runimal 유형 보기").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `TierChange는 직전·이번 티어 순서로 승급·유지·강등을 고르고 모르면 UNKNOWN이다`() {
        assertEquals(TierChange.UP, TierChange.of("SILVER", "GOLD"))
        assertEquals(TierChange.KEEP, TierChange.of("GOLD", "GOLD"))
        assertEquals(TierChange.DOWN, TierChange.of("MASTER", "PLATINUM"))
        assertEquals(TierChange.UNKNOWN, TierChange.of(null, "GOLD"))
        assertEquals(TierChange.UNKNOWN, TierChange.of("DIAMOND", "GOLD"))
        assertEquals(TierChange.UNKNOWN, TierChange.of("UNRANKED", "GOLD"))
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
            runimalDescription = "같은 길을 같은 시간에, 오래 꾸준히 달려요",
            previousTier = "GOLD"
        )
    }
}
