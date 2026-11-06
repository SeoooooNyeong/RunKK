package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.a207.runkk.app.core.model.TierDisplay
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 티어 배지(FR-283, D-506, J24-21): 글자와 낭독 이름을 함께 보이고, 글자 크기를 키우면 함께 커진다. */
@RunWith(RobolectricTestRunner::class)
class TierBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-5819 배지는 글자와 낭독 이름을 함께 보인다`() {
        compose.setContent {
            RunkkTheme(darkTheme = false, reduceMotion = true) {
                Column {
                    TierBadge(TierDisplay.Confirmed("GOLD"), TierBadgeSize.XSMALL)
                    TierBadge(TierDisplay.Provisional("GOLD"), TierBadgeSize.SMALL)
                    TierBadge(TierDisplay.Placing, TierBadgeSize.SMALL)
                    TierBadgeLarge(TierDisplay.Confirmed("MASTER"))
                }
            }
        }
        compose.onNodeWithContentDescription("골드 티어").assertExists()
        compose.onNodeWithContentDescription("임시 골드 티어").assertExists()
        compose.onNodeWithContentDescription("배치 중").assertExists()
        compose.onNodeWithContentDescription("마스터 티어").assertExists()
        // 색만으로 가르지 않고 글자를 늘 함께 그린다.
        compose.onNodeWithText("골드", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("임시 골드", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("배치 중", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("마스터", useUnmergedTree = true).assertExists()
        // 엠블럼 그림은 낭독하지 않는다(그림에 설명이 없다). 배지는 누르는 것이 아니라 역할이 없다.
        compose.onNodeWithContentDescription("골드 티어")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        compose.onNodeWithContentDescription("골드", substring = false).assertDoesNotExist()
    }

    @Test
    fun `TC-5820 글자 크기를 키우면 배지 높이가 함께 커진다`() {
        compose.setContent {
            val density = LocalDensity.current.density
            RunkkTheme(darkTheme = false, reduceMotion = true) {
                Column {
                    listOf(1f, 2f).forEach { scale ->
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = scale)) {
                            TierBadge(
                                TierDisplay.Confirmed("PLATINUM"),
                                TierBadgeSize.XSMALL,
                                Modifier.testTag("badge-$scale")
                            )
                        }
                    }
                }
            }
        }
        val normal = compose.onNodeWithTag("badge-1.0").getUnclippedBoundsInRoot().height.value
        val large = compose.onNodeWithTag("badge-2.0").getUnclippedBoundsInRoot().height.value
        assertTrue(normal >= TierBadgeSize.XSMALL.height.value, "기본 높이 20dp 이상: $normal")
        assertTrue(large > TierBadgeSize.XSMALL.height.value && large > normal, "글자 2배면 더 높다: $normal → $large")
    }
}
