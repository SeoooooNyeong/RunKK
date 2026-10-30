package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 상단 바(높이 64): 뒤로 버튼(있으면) + 제목(18, 900) + 오른쪽 [actions]. [isTitleCentered]면 제목을 바 가운데에 둔다(A-2).
 */
@Composable
fun RunkkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    isTitleCentered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) RunkkBackButton(onClick = onBack)
            if (!isTitleCentered) TopBarTitle(title)
            Spacer(Modifier.weight(1f))
            actions()
        }
        if (isTitleCentered) TopBarTitle(title)
    }
}

@Composable
private fun TopBarTitle(title: String) {
    Text(
        text = title,
        style = RunkkTextStyles.TopBarTitle,
        color = RunkkColors.TextPrimary,
        modifier = Modifier.semantics { heading() }
    )
}

/** 뒤로 버튼(보이는 크기 40dp, 터치 48dp). 라이트는 보라 채움 원에 흰 화살표, 다크는 표면 원이다(R2-05, D85). */
@Composable
fun RunkkBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RunkkIconButton(
        RunkkIcons.Back,
        contentDescription = stringResource(R.string.core_bars_back),
        onClick = onClick,
        modifier = modifier,
        borderColor = RunkkColors.BackButtonBorder,
        containerColor = RunkkColors.BackButton,
        iconTint = RunkkColors.OnBackButton
    )
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
            if (onBack != null) RunkkBackButton(onClick = onBack)
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

/** 하단 탭 바의 높이(떠 있는 탭 줄 68 + 아래 틈 12). 시스템 내비게이션 영역은 따로 더한다. 탭 화면의 아래 여백이 이 값을 쓴다. */
val RUNKK_BOTTOM_BAR_HEIGHT: Dp = 80.dp

/**
 * 하단 탭 바(높이 [RUNKK_BOTTOM_BAR_HEIGHT] + 시스템 내비게이션 영역, J-0). 양옆·아래를 띄운 둥근 표면 위에 탭을 놓는다.
 * 고른 탭은 라임 + 굵게, 가운데 홈은 원형 버튼이다.
 * [lockedTabs]의 탭은 아이콘 오른쪽 아래에 자물쇠를 겹치고 아이콘·라벨을 흐리게 한다(D-253). 잠긴 탭을 눌러도 [onSelect]는
 * 불리고, 이동 대신 안내를 띄우는 것은 부르는 쪽(RunkkNavHost)이 정한다. 탭은 첫 사용 가이드 앵커를 적는다(D-277).
 */
@Composable
fun RunkkBottomBar(
    selected: RunkkTab?,
    onSelect: (RunkkTab) -> Unit,
    modifier: Modifier = Modifier,
    lockedTabs: Set<RunkkTab> = emptySet()
) {
    val shape = RoundedCornerShape(BOTTOM_BAR_CORNER)
    // 바깥은 비워 두고 둥근 표면만 그린다(떠 있는 탭 바). 홈 배경 장면이 탭 바 뒤로 이어 보인다(R2-06, D86).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(RUNKK_BOTTOM_BAR_HEIGHT)
            .padding(start = BOTTOM_BAR_SIDE, end = BOTTOM_BAR_SIDE, bottom = BOTTOM_BAR_GAP)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.Border, shape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RunkkTab.entries.forEach { tab ->
                BottomTab(
                    tab = tab,
                    isSelected = tab == selected,
                    isLocked = tab in lockedTabs,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

/** 하단 탭 하나: 아이콘(홈은 원 안) + 라벨. 잠긴 탭은 자물쇠와 흐린 색, 보조 기술에는 "잠김"으로 읽힌다. */
@Composable
private fun BottomTab(
    tab: RunkkTab,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isSelected) RunkkColors.Lime else RunkkColors.TextSecondary
    val lockedState = stringResource(R.string.core_tab_locked)
    val anchor = tab.guideStep?.let { Modifier.guideAnchor(it) } ?: Modifier
    Column(
        modifier = modifier
            .then(anchor)
            .clip(RunkkShapes.Medium)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { if (isLocked) stateDescription = lockedState }
            .alpha(if (isLocked) LOCKED_ALPHA else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (tab == RunkkTab.HOME) {
                Box(
                    modifier = Modifier
                        .size(HOME_CIRCLE)
                        .clip(CircleShape)
                        .background(RunkkColors.Surface)
                        .border(1.dp, if (isSelected) RunkkColors.Lime else RunkkColors.Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            } else {
                Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            if (isLocked) {
                Icon(
                    RunkkIcons.Lock,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).size(12.dp)
                )
            }
        }
        Text(
            text = stringResource(tab.labelRes),
            style = RunkkTextStyles.text(11, if (isSelected) FontWeight.Bold else FontWeight.Normal),
            color = color
        )
    }
}

private const val LOCKED_ALPHA = 0.4f
private val HOME_CIRCLE: Dp = 44.dp

private val BOTTOM_BAR_CORNER: Dp = 24.dp
private val BOTTOM_BAR_SIDE: Dp = 18.dp
private val BOTTOM_BAR_GAP: Dp = 12.dp

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
            Box(Modifier.fillMaxWidth()) {
                RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}, lockedTabs = setOf(RunkkTab.CREW))
            }
        }
    }
}
