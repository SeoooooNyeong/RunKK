package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.TierDisplay
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
            }, size = RunkkButtonSize.Hero, hasTrailingArrow = true, modifier = Modifier.fillMaxWidth())
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
                RunkkInfoChip("5km", isHighlighted = true, isDisplay = true)
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
            RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}, lockedTabs = setOf(RunkkTab.CREW))
        }
    }

    /** TC-5821 티어 배지(피그마 61354:2910): 확정·임시·배치 중 × 아주 작게·작게, 크게(확정 5·배치 중). */
    @Test
    fun designsystemTierBadges() = compose.captureRunkkScreen("designsystem_tier_badges", isDarkTheme = false) {
        TierBadgeSheet()
    }

    @Test
    fun designsystemTierBadgesDark() = compose.captureRunkkScreen("designsystem_tier_badges_dark") {
        TierBadgeSheet()
    }

    /** TC-5822 티어 엠블럼(피그마 61363:3403): 6티어 × 큰 112·작은 20(아주 작게 14). */
    @Test
    fun designsystemTierEmblems() = compose.captureRunkkScreen("designsystem_tier_emblems", isDarkTheme = false) {
        TierEmblemSheet()
    }

    @Test
    fun designsystemTierEmblemsDark() = compose.captureRunkkScreen("designsystem_tier_emblems_dark") {
        TierEmblemSheet()
    }

    /** 기본 머리(남녀, 큰 그림)와 홈 상단(48dp)·마이 프로필(56dp) 원 안에 넣었을 때(D-812). */
    @Test
    fun avatarDefaultHeads() = compose.captureRunkkScreen("designsystem_avatar_default_heads") {
        val characters = AvatarCharacter.entries
        Column(
            Modifier.fillMaxSize().background(RunkkColors.Background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                characters.forEach { character ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(RunkkColors.Surface)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RunkkAvatarPortrait(
                            RunkkAvatarArt.DEFAULT_FACE_KEY,
                            emptyList(),
                            Modifier.size(120.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh),
                            character
                        )
                        Text(character.name, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                    }
                }
            }
            listOf(44, 56, 120).forEach { size ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    characters.forEach { character ->
                        RunkkAvatarPortrait(
                            null,
                            emptyList(),
                            Modifier.size(size.dp).border(2.dp, RunkkColors.Lime, CircleShape).padding(3.dp)
                                .clip(CircleShape).background(RunkkColors.SurfacePressed),
                            character
                        )
                    }
                }
            }
        }
    }

    /**
     * 프로필 원의 셀카 머리 구도(D-821): 기본 머리와 셀카 머리(도형 픽스처: 캔버스 가운데 작은 머리·짧은 목)를 견준다. 셀카 머리도
     * 기본 머리와 같은 크기·자리이고 목이 원 아래 가장자리까지 이어진다. 성별마다 위 줄 기본 머리, 아래 줄 셀카 머리(44·88·120dp).
     */
    @Test
    fun avatarSelfieHeadPortraits() = compose.captureRunkkScreen("designsystem_avatar_selfie_head_portraits") {
        val layers = SelfieHeadFixture.layers
        Column(
            Modifier.fillMaxSize().background(RunkkColors.Background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AvatarCharacter.entries.forEach { character ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(44, 88, 120).forEach { size ->
                        val circle = Modifier.size(size.dp).clip(CircleShape).background(RunkkColors.SurfacePressed)
                        RunkkAvatarPortrait(RunkkAvatarArt.DEFAULT_FACE_KEY, emptyList(), circle, character)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(44, 88, 120).forEach { size ->
                        val circle = Modifier.size(size.dp).clip(CircleShape).background(RunkkColors.SurfacePressed)
                        RunkkAvatarPortrait("head:1:job-1", layers, circle, character)
                    }
                }
            }
        }
    }

    /** 피그마 맞춤 W1 공통 부품(라이트·다크, S15P31A207-292). */
    @Test
    fun figmaPartsLight() = compose.captureRunkkScreen("designsystem_figma_parts_light", isDarkTheme = false) {
        FigmaParts()
    }

    @Test
    fun figmaPartsDark() = compose.captureRunkkScreen("designsystem_figma_parts_dark") { FigmaParts() }
}

@Composable
private fun FigmaParts() {
    Column(
        Modifier.fillMaxSize().background(RunkkColors.Background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunkkTopBar(title = "크루런 결과", onBack = {})
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RunkkButton("확인", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RunkkButton(
                    "취소",
                    onClick = {},
                    style = RunkkButtonStyle.Outline,
                    size = RunkkButtonSize.Medium,
                    modifier = Modifier.weight(1f)
                )
                RunkkButton(
                    "탈퇴하기",
                    onClick = {},
                    style = RunkkButtonStyle.Purple,
                    size = RunkkButtonSize.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkButton("다음", onClick = {}, isEnabled = false, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RunkkButton("초대 코드", onClick = {}, style = RunkkButtonStyle.Outline, size = RunkkButtonSize.Small)
                RunkkButton("가입", onClick = {}, size = RunkkButtonSize.Small)
                RunkkButton("더보기", onClick = {}, style = RunkkButtonStyle.White, size = RunkkButtonSize.XSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkChoiceChip("아침", isSelected = false, onSelectedChange = {})
                RunkkChoiceChip("저녁", isSelected = true, onSelectedChange = {})
            }
            RunkkSegmented(listOf("공개", "비공개"), selectedIndex = 0, onSelect = {})
            RunkkSegmented(
                listOf("레이스", "크루런", "채팅"),
                selectedIndex = 1,
                onSelect = {},
                selection = RunkkSegmentedSelection.Lime
            )
            RunkkSegmentRow(listOf("요약", "러닝별", "월간"), selectedIndex = 0, onSelect = {})
            MetaRow(
                listOf(
                    MetaItem(RunkkIcons.CalendarToday, "2026.10.12 개설"),
                    MetaItem(RunkkIcons.Group, "7명"),
                    MetaItem(RunkkIcons.Schedule, "평일 저녁"),
                    MetaItem(RunkkIcons.DirectionsRun, "5km")
                )
            )
            MetaRow(
                listOf(
                    MetaItem(RunkkIcons.CheckCircle, "완료"),
                    MetaItem(RunkkIcons.EmojiEvents, "골드"),
                    MetaItem(RunkkIcons.TrendingUp, "Lv23"),
                    MetaItem(RunkkIcons.Person, "20대")
                )
            )
            RunkkCard(modifier = Modifier.fillMaxWidth()) {
                Text("테두리 없는 카드", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
                Text("본문 14/22, Noto Sans KR", style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
            }
            Text("2.14", style = RunkkTextStyles.DisplayHuge, color = RunkkColors.TextPrimary)
        }
    }
}

private val TIERS = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER")

/** 배지 견본: 맨 위 배치 중, 아주 작게(확정·임시, 두 티어씩), 작게(확정·임시, 한 티어씩), 아래는 크게. */
@Composable
private fun TierBadgeSheet() {
    Column(
        Modifier.fillMaxSize().background(RunkkColors.Background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TierBadge(TierDisplay.Placing, TierBadgeSize.XSMALL)
            TierBadge(TierDisplay.Placing, TierBadgeSize.SMALL)
        }
        TIERS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { tier ->
                    TierBadge(TierDisplay.Confirmed(tier), TierBadgeSize.XSMALL)
                    TierBadge(TierDisplay.Provisional(tier), TierBadgeSize.XSMALL)
                }
            }
        }
        TIERS.forEach { tier ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TierBadge(TierDisplay.Confirmed(tier), TierBadgeSize.SMALL)
                TierBadge(TierDisplay.Provisional(tier), TierBadgeSize.SMALL)
            }
        }
        listOf(
            listOf<TierDisplay>(TierDisplay.Placing) + TIERS.take(2).map { TierDisplay.Confirmed(it) },
            TIERS.drop(2).map { TierDisplay.Confirmed(it) }
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { TierBadgeLarge(it) } }
        }
    }
}

/** 엠블럼 견본: 줄마다 한 티어(큰 112, 작은 20, 아주 작게 14). */
@Composable
private fun TierEmblemSheet() {
    Column(
        Modifier.fillMaxSize().background(RunkkColors.Background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (listOf("UNRANKED") + TIERS).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { tier ->
                    TierEmblem(tier, TierEmblemSize.LARGE)
                    TierEmblem(tier, TierEmblemSize.SMALL)
                    TierEmblem(tier, TierEmblemSize.XSMALL)
                }
            }
        }
    }
}
