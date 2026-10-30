package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
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

/** 하단 탭 바의 높이(고른 탭 원이 솟는 20 + 떠 있는 표면 70 + 아래 틈 12). 시스템 내비게이션 영역은 따로 더한다. 탭 화면의 아래 여백이 이 값을 쓴다. */
val RUNKK_BOTTOM_BAR_HEIGHT: Dp = 102.dp

/**
 * 하단 탭 바(높이 [RUNKK_BOTTOM_BAR_HEIGHT] + 시스템 내비게이션 영역, 피그마 Navigation/Floating, D96). 양옆·아래를 띄운 둥근 표면 위에
 * 탭을 놓고, 고른 탭은 표면 위로 솟은 흰 원(그림자) 안에 아이콘을 두며 그 아래 라벨을 보라 굵게 쓴다. 표면은 원 아래를 오목하게 판다.
 * 고른 탭이 바뀌면 원과 오목한 자리가 함께 옮긴다(300ms, 움직임 줄이기면 바로).
 * [lockedTabs]의 탭은 아이콘 오른쪽 아래에 자물쇠를 겹치고 아이콘·라벨을 흐리게 한다(D-253). 잠긴 탭을 눌러도 [onSelect]는
 * 불리고, 이동 대신 안내를 띄우는 것은 부르는 쪽(RunkkNavHost)이 정한다. 탭은 표면 안 자기 칸을 첫 사용 가이드 앵커로 적는다(D-277).
 */
@Composable
fun RunkkBottomBar(
    selected: RunkkTab?,
    onSelect: (RunkkTab) -> Unit,
    modifier: Modifier = Modifier,
    lockedTabs: Set<RunkkTab> = emptySet()
) {
    val tabs = RunkkTab.entries
    val selectedIndex = selected?.let(tabs::indexOf) ?: -1
    // 고른 탭 가운데의 가로 위치(표면 너비에 대한 비율). 고른 탭이 없으면 원과 오목한 자리를 그리지 않는다.
    val target = if (selectedIndex < 0) NO_SELECTION else (selectedIndex + HALF) / tabs.size
    val isReduced = LocalReduceMotion.current
    val center by animateFloatAsState(
        targetValue = target,
        animationSpec = if (isReduced || target == NO_SELECTION) snap() else tween(INDICATOR_MILLIS, easing = EaseOut),
        label = "bottomBarIndicator"
    )
    val isDark = LocalRunkkColors.current.isDark
    // 바깥은 비워 두고 표면만 그린다(떠 있는 탭 바). 홈 배경 장면이 탭 바 뒤로 이어 보인다(R2-06, D86).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(RUNKK_BOTTOM_BAR_HEIGHT)
            .padding(start = BOTTOM_BAR_SIDE, end = BOTTOM_BAR_SIDE, bottom = BOTTOM_BAR_GAP)
    ) {
        val centerX = maxWidth * center
        val surfaceShape = FloatingBarShape(notchCenter = if (center < 0f) null else centerX)
        Box(
            modifier = Modifier
                .padding(top = BOTTOM_BAR_RISE)
                .fillMaxWidth()
                .height(BOTTOM_BAR_SURFACE)
                .testTag(BOTTOM_BAR_SURFACE_TAG)
                .dropShadow(surfaceShape, SurfaceShadow)
                .clip(surfaceShape)
                .background(RunkkColors.Surface)
                .then(if (isDark) Modifier.border(1.dp, RunkkColors.Border, surfaceShape) else Modifier)
        )
        if (center >= 0f) {
            Box(
                modifier = Modifier
                    .offset(x = centerX - INDICATOR_SIZE / 2)
                    .size(INDICATOR_SIZE)
                    .testTag(BOTTOM_BAR_INDICATOR_TAG)
                    .dropShadow(CircleShape, IndicatorShadow)
                    .clip(CircleShape)
                    .background(RunkkColors.Surface)
                    .then(if (isDark) Modifier.border(1.dp, RunkkColors.Border, CircleShape) else Modifier)
            )
        }
        Row(
            modifier = Modifier
                .padding(top = BOTTOM_BAR_RISE)
                .fillMaxWidth()
                .height(BOTTOM_BAR_SURFACE)
                .selectableGroup()
        ) {
            tabs.forEach { tab ->
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

/**
 * 하단 탭 하나(표면 높이 70 칸): 아이콘(위 14) + 라벨(위 40). 고른 탭은 아이콘을 솟은 원 가운데로 올리고 라벨을 보라 굵게 쓴다.
 * 잠긴 탭은 자물쇠와 흐린 색, 보조 기술에는 "잠김"으로 읽힌다.
 */
@Composable
private fun BottomTab(
    tab: RunkkTab,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lockedState = stringResource(R.string.core_tab_locked)
    val anchor = tab.guideStep?.let { Modifier.guideAnchor(it) } ?: Modifier
    Column(
        modifier = modifier
            .then(anchor)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { if (isLocked) stateDescription = lockedState }
            .alpha(if (isLocked) LOCKED_ALPHA else 1f)
            .padding(top = TAB_ICON_TOP),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TAB_LABEL_GAP)
    ) {
        Box(
            modifier = Modifier.offset(y = if (isSelected) -SELECTED_ICON_LIFT else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = if (isSelected) RunkkColors.TextPrimary else RunkkColors.TextSecondary,
                modifier = Modifier.size(TAB_ICON)
            )
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
            color = if (isSelected) RunkkColors.Lavender else RunkkColors.TextSecondary
        )
    }
}

/**
 * 떠 있는 표면 모양: 모서리 24 둥근 네모에서 [notchCenter] 아래를 오목하게 판다(피그마 Concave, 폭 78, 깊이 32). 오목한 자리가 끝 탭에
 * 걸리면 둥근 네모 밖은 잘린다. [notchCenter]가 null이면 둥근 네모다.
 */
private class FloatingBarShape(private val notchCenter: Dp?) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val corner = BOTTOM_BAR_CORNER.toPx()
            val rounded = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
            }
            val center = notchCenter?.toPx() ?: return Outline.Generic(rounded)
            val half = NOTCH_HALF.toPx()
            val depth = NOTCH_DEPTH.toPx()
            val near = NOTCH_NEAR.toPx()
            val far = NOTCH_FAR.toPx()
            val notched = Path().apply {
                moveTo(-half, 0f)
                lineTo(center - half, 0f)
                cubicTo(center - near, 0f, center - far, depth, center, depth)
                cubicTo(center + far, depth, center + near, 0f, center + half, 0f)
                lineTo(size.width + half, 0f)
                lineTo(size.width + half, size.height)
                lineTo(-half, size.height)
                close()
            }
            Outline.Generic(Path.combine(PathOperation.Intersect, rounded, notched))
        }
}

/** 표면 그림자(피그마 0 6 10, #261F33 14%). */
private val SurfaceShadow = Shadow(radius = 10.dp, color = Color(0x24261F33), offset = DpOffset(0.dp, 6.dp))

/** 솟은 원 그림자(피그마 0 2 8, #261F33 8%). */
private val IndicatorShadow = Shadow(radius = 8.dp, color = Color(0x14261F33), offset = DpOffset(0.dp, 2.dp))

/** 하단 탭 바 표면의 시험 표지. */
internal const val BOTTOM_BAR_SURFACE_TAG = "runkk-bottom-bar-surface"

/** 고른 탭 원의 시험 표지. */
internal const val BOTTOM_BAR_INDICATOR_TAG = "runkk-bottom-bar-indicator"

private const val LOCKED_ALPHA = 0.4f
private const val HALF = 0.5f
private const val NO_SELECTION = -1f
private const val INDICATOR_MILLIS = 300

private val BOTTOM_BAR_RISE: Dp = 20.dp
private val BOTTOM_BAR_SURFACE: Dp = 70.dp
private val BOTTOM_BAR_CORNER: Dp = 24.dp
private val BOTTOM_BAR_SIDE: Dp = 18.dp
private val BOTTOM_BAR_GAP: Dp = 12.dp
private val INDICATOR_SIZE: Dp = 46.dp
private val NOTCH_HALF: Dp = 39.dp
private val NOTCH_DEPTH: Dp = 32.dp
private val NOTCH_NEAR: Dp = 29.dp
private val NOTCH_FAR: Dp = 32.dp
private val TAB_ICON: Dp = 24.dp
private val TAB_ICON_TOP: Dp = 14.dp
private val TAB_LABEL_GAP: Dp = 2.dp

/** 고른 탭 아이콘을 원 가운데(표면 위 -9)로 올리는 거리. */
private val SELECTED_ICON_LIFT: Dp = 23.dp

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
