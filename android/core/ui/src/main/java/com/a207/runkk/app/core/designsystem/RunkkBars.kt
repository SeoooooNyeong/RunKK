package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 상단 바(높이 64): 뒤로 버튼(있으면) + 제목(18, 900) + 오른쪽 [actions].
 */
@Composable
fun RunkkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            RunkkIconButton(
                RunkkIcons.Back,
                contentDescription = stringResource(R.string.core_bars_back),
                onClick = onBack,
                modifier = Modifier.padding(end = 0.dp)
            )
        }
        Text(
            text = title,
            style = RunkkTextStyles.TopBarTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                heading()
            }
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * 단계 상단 바(가입 1/5 …): 뒤로 + 제목(16, 700) + "n / 전체"(Barlow) + 아래 4dp 진행 막대.
 */
@Composable
fun RunkkStepTopBar(title: String, step: Int, total: Int, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val stepDescription = stringResource(R.string.core_bars_step_progress, step, total)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack !=
                null
            ) {
                RunkkIconButton(
                    RunkkIcons.Back,
                    contentDescription = stringResource(R.string.core_bars_back),
                    onClick = onBack
                )
            }
            Text(
                text = title,
                style = RunkkTextStyles.StepTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = RunkkColors.Lime)) { append("$step") }
                    withStyle(SpanStyle(color = RunkkColors.TextSecondary)) { append(" / $total") }
                },
                style = RunkkTextStyles.display(20),
                modifier = Modifier.clearAndSetSemantics { contentDescription = stepDescription }
            )
        }
        RunkkProgressBar(
            progress = step.toFloat() / total,
            height = 4.dp,
            modifier = Modifier.padding(horizontal = 16.dp).clearAndSetSemantics {}
        )
    }
}

/** 하단 탭 다섯 개(셀프·셀럽·홈·크루·마이, SRS 5.1). 경로 이름과의 대응은 app이 정한다. */
enum class RunkkTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    SELF(R.string.core_tab_self, RunkkIcons.History),
    CELEB(R.string.core_tab_celeb, RunkkIcons.Star),
    HOME(R.string.core_tab_home, RunkkIcons.Home),
    CREW(R.string.core_tab_crew, RunkkIcons.Users),
    ME(R.string.core_tab_me, RunkkIcons.User)
}

/** 하단 탭 바의 높이(구분선 1 + 탭 줄 79). 시스템 내비게이션 영역은 따로 더한다. 탭 화면의 아래 여백이 이 값을 쓴다. */
val RUNKK_BOTTOM_BAR_HEIGHT: Dp = 80.dp

/**
 * 하단 탭 바(높이 [RUNKK_BOTTOM_BAR_HEIGHT] + 시스템 내비게이션 영역). 고른 탭은 라임 + 굵게. 보조 기술에는 탭으로 보인다.
 */
@Composable
fun RunkkBottomBar(selected: RunkkTab?, onSelect: (RunkkTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().background(
            RunkkColors.Background
        ).windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(thickness = BOTTOM_BAR_DIVIDER, color = RunkkColors.Border)
        Row(
            modifier = Modifier.fillMaxWidth().height(
                RUNKK_BOTTOM_BAR_HEIGHT - BOTTOM_BAR_DIVIDER
            ).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) RunkkColors.Lime else RunkkColors.TextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RunkkShapes.Medium)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = RunkkTextStyles.text(11, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        color = color
                    )
                }
            }
        }
    }
}

private val BOTTOM_BAR_DIVIDER: Dp = 1.dp

/** 메뉴 한 줄(마이·설정): 아이콘(선택) + 제목 + 값(선택) + 오른쪽 화살표. 높이 56 이상. */
@Composable
fun RunkkMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    isAccent: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon !=
            null
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isAccent) RunkkColors.Lavender else RunkkColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = if (isAccent) RunkkColors.Lavender else RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (value != null) Text(text = value, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary)
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 영문 오버라인(16, 넓은 자간: "LIVE BATTLE · 5km"). 보조 기술에는 [spoken](기본: 한글로 바꾼 라벨, "실시간 대결, 5킬로미터")으로
 * 읽힌다. [isHeading]이면 화면 제목으로 알린다.
 */
@Composable
fun RunkkOverline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RunkkColors.TextSecondary,
    isHeading: Boolean = false,
    spoken: String = RunkkFormat.spokenLabel(text)
) {
    Text(
        text = text,
        style = RunkkTextStyles.Overline,
        color = color,
        modifier = modifier.semantics {
            contentDescription = spoken
            if (isHeading) heading()
        }
    )
}

/** 구역 제목(13, 700, 보조색): "최신순", "알림". 보조 기술에 제목으로 알린다. */
@Composable
fun RunkkSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = RunkkTextStyles.text(13, FontWeight.Bold),
        color = RunkkColors.TextSecondary,
        modifier = modifier.semantics {
            heading()
        }
    )
}

@Preview(name = "상단·하단 바", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkBarsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkTopBar(title = "내 기록", onBack = {}) {
                RunkkSegmentedTabs(listOf("달력", "목록"), selectedIndex = 0, onSelect = {}, isCompact = true)
            }
            RunkkStepTopBar(title = "회원 가입", step = 1, total = 5, onBack = {})
            RunkkMenuRow("설정", onClick = {}, icon = RunkkIcons.Settings, value = "km")
            RunkkSectionTitle("최신순", modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxWidth()) { RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}) }
        }
    }
}
