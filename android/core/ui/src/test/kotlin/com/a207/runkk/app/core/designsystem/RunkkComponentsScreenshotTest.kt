package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 디자인 시스템 구성 요소 모음 스크린샷(390×844dp, 다크). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunkkComponentsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonsAndChips() = compose.captureRunkkScreen("designsystem_buttons_chips") {
        Column(
            Modifier.fillMaxSize().background(RunkkColors.Background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkPrimaryButton("카카오로 시작하기", onClick = {}, modifier = Modifier.fillMaxWidth())
            RunkkPrimaryButton("자유런 시작", onClick = {
            }, size = RunkkButtonSize.Large, hasTrailingArrow = true, modifier = Modifier.fillMaxWidth())
            RunkkSecondaryButton("Google로 시작하기", onClick = {}, modifier = Modifier.fillMaxWidth())
            RunkkAccentOutlinedButton("회원 탈퇴", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkPillButton("카드 공유", onClick = {}, leadingIcon = RunkkIcons.Share)
                RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = {}, hasBadge = true)
            }
            RunkkChipGroup("동행") {
                RunkkChoiceChip("혼자가 편해요", isSelected = false, onSelectedChange = {})
                RunkkChoiceChip("함께가 좋아요", isSelected = true, onSelectedChange = {})
            }
            RunkkSegmentRow(listOf("전체공개", "크루공개", "비공개"), selectedIndex = 0, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkInfoChip("5K", isHighlighted = true, isDisplay = true)
                RunkkInfoChip("음성 켬")
                RunkkBadge("유효 러닝", tone = RunkkBadgeTone.Lime)
            }
            RunkkSegmentedTabs(listOf("자유런", "배틀런"), selectedIndex = 0, onSelect = {
            }, modifier = Modifier.fillMaxWidth())
            RunkkLongPressButton(onTap = {
            }, onLongPress = {}, tapLabel = "종료 안내", longPressLabel = "길게 눌러 종료", caption = "길게 눌러 종료") {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.Lavender))
            }
        }
    }

    @Test
    fun cardsAndBars() = compose.captureRunkkScreen("designsystem_cards_bars") {
        Column(
            Modifier.fillMaxSize().background(RunkkColors.Background),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkStepTopBar(title = "회원 가입", step = 2, total = 5, onBack = {})
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RunkkBanner("최장 거리 신기록 · 직전 4.80km", isStrong = true)
                RunkkBanner("2km 통과 · 11:04 · 평균 5:32")
                RunkkBigNumber("2.14", caption = "KILOMETERS")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunkkStatTile(
                        "오늘",
                        StatTileValue("3.02", unit = "KM"),
                        caption = "1회",
                        modifier = Modifier.weight(1f)
                    )
                    RunkkStatTile(
                        "이번 주 목표",
                        StatTileValue("60", unit = "%"),
                        style = StatTileStyle(valueColor = RunkkColors.Lime),
                        modifier = Modifier.weight(1f)
                    ) {
                        RunkkProgressBar(0.6f)
                    }
                }
                RunkkCard(isDashed = true) {
                    Text("미전송 기록 1건", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                }
                RunkkErrorState("네트워크에 연결할 수 없어요", onRetry = {})
            }
            Box(Modifier.weight(1f))
            RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {})
        }
    }
}
