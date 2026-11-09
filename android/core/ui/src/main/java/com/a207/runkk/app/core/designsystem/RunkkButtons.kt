package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 기본 CTA 크기: 일반 56dp(모서리 16), 크게 72dp(모서리 20, 홈 "자유런 시작"). */
enum class RunkkButtonSize(val height: Dp, val corner: Dp) {
    Regular(56.dp, 16.dp),
    Large(72.dp, 20.dp)
}

/** 누르면 살짝 줄어드는 효과(시안 :active scale). 동작 줄이기면 줄이지 않는다. */
@Composable
fun Modifier.runkkPressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val target = if (pressed && !reduce) pressedScale else 1f
    val scale by animateFloatAsState(target, animationSpec = tween(if (reduce) 0 else 200), label = "pressScale")
    return this.scale(scale)
}

/**
 * 기본 CTA(라임). 화면마다 주 행동 하나에만 쓴다.
 * [hasTrailingArrow]가 참이면 시안 홈 "자유런 시작"처럼 오른쪽에 검은 원 화살표를 단다.
 */
@Composable
fun RunkkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    size: RunkkButtonSize = RunkkButtonSize.Regular,
    hasTrailingArrow: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(size.corner)
    val loadingDescription = stringResource(R.string.core_buttons_loading)
    val active = isEnabled && !isLoading
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = text
                if (isLoading) stateDescription = loadingDescription
            }
            .heightIn(min = size.height)
            .runkkPressScale(interaction)
            .clip(shape)
            .background(if (pressed) RunkkColors.LimeFillPressed else RunkkColors.LimeFill)
            .runkkDisabledAlpha(isEnabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                color = RunkkColors.OnLimeFill,
                strokeWidth = 3.dp
            )
        } else {
            RunkkPrimaryButtonContent(text, size, hasTrailingArrow)
        }
    }
}

/** 기본 CTA의 글자와(있으면) 오른쪽 검은 원 화살표. */
@Composable
private fun RunkkPrimaryButtonContent(text: String, size: RunkkButtonSize, hasTrailingArrow: Boolean) {
    Text(
        text = text,
        style = if (size ==
            RunkkButtonSize.Large
        ) {
            RunkkTextStyles.text(24, FontWeight.Black)
        } else {
            RunkkTextStyles.Button
        },
        color = RunkkColors.OnLimeFill
    )
    if (hasTrailingArrow) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(RunkkColors.OnLimeFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.ArrowRight,
                contentDescription = null,
                tint = RunkkColors.LimeFill,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 보조 버튼(표면 + 강한 테두리). 주 행동 옆의 다른 선택지에 쓴다. */
@Composable
fun RunkkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Dp = 56.dp
) {
    OutlineButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon,
        height = height,
        borderColor = RunkkColors.BorderStrong,
        contentColor = RunkkColors.TextPrimary
    )
}

/** 외곽선 강조 버튼(라벤더): 종료·그만두기·탈퇴처럼 되돌리기 어려운 행동. */
@Composable
fun RunkkAccentOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Dp = 56.dp
) {
    OutlineButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon,
        height = height,
        borderColor = RunkkColors.Lavender,
        contentColor = RunkkColors.Lavender
    )
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    isEnabled: Boolean,
    leadingIcon: ImageVector?,
    height: Dp,
    borderColor: Color,
    contentColor: Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RunkkShapes.Medium
    Row(
        modifier = modifier
            .heightIn(min = height)
            .runkkPressScale(interaction)
            .clip(shape)
            .background(if (pressed) RunkkColors.SurfacePressed else RunkkColors.Surface)
            .border(1.dp, if (pressed) RunkkColors.BorderHover else borderColor, shape)
            .runkkDisabledAlpha(isEnabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon !=
            null
        ) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(text = text, style = RunkkTextStyles.text(15, FontWeight.Bold), color = contentColor)
    }
}

/** 알약 버튼(높이 40, 모서리 20): "취소", "사진 선택", "카드 공유" 같은 작은 행동. 터치 영역은 48dp를 보장한다. */
@Composable
fun RunkkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isEnabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .runkkPressScale(interaction)
                .clip(shape)
                .background(if (pressed) RunkkColors.SurfacePressed else RunkkColors.Surface)
                .border(1.dp, if (pressed) RunkkColors.BorderHover else RunkkColors.BorderStrong, shape)
                .runkkDisabledAlpha(isEnabled)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon !=
                null
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text = text, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary)
        }
    }
}

/**
 * 라임 알약 버튼(높이 40, 모서리 20, 글자 13 굵게): 시안 크루 "+ 만들기"·"가입"처럼 작은 주 행동. 터치 영역은 48dp를 보장한다.
 */
@Composable
fun RunkkLimePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isEnabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = LIME_PILL_MIN_HEIGHT)
                .runkkPressScale(interaction)
                // 꺼지면 배경까지 흐리게(alpha는 뒤에 오는 그리기에만 걸린다).
                .runkkDisabledAlpha(isEnabled)
                .clip(LIME_PILL_SHAPE)
                .background(RunkkColors.LimeFill)
                .padding(horizontal = LIME_PILL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = RunkkColors.OnLimeFill,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text, style = RunkkTextStyles.text(13, FontWeight.Black), color = RunkkColors.OnLimeFill)
        }
    }
}

/**
 * 라임 알약 표시(누르지 않는 값 알약, 홈 리워드 잔액 등): [RunkkLimePillButton]과 같은 모양(최소 높이 40, 모서리 20).
 * 높이를 고정하지 않아 기기 글자 크기를 키우면 알약도 함께 커진다(고정 높이 안에서 글자가 잘리지 않게, 사용자 요청 2026-10-31).
 */
@Composable
fun RunkkLimePill(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = RunkkTextStyles.text(13, FontWeight.Black)
) {
    Box(
        modifier = modifier
            .heightIn(min = LIME_PILL_MIN_HEIGHT)
            .clip(LIME_PILL_SHAPE)
            .background(RunkkColors.LimeFill)
            .padding(horizontal = LIME_PILL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = textStyle, color = RunkkColors.OnLimeFill)
    }
}

/**
 * 원형 아이콘 버튼(보이는 크기 40dp, 터치 영역 48dp). [hasBadge]가 참이면 원 오른쪽 위에 [RunkkNotificationDot]을 단다(새 알림,
 * 점 중심이 원 가장자리에서 4dp 안쪽, 피그마 알림 점).
 * [contentDescription]은 필수다(아이콘만 있는 버튼). 원 색은 [containerColor], 아이콘 색은 [iconTint]다.
 */
@Composable
fun RunkkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasBadge: Boolean = false,
    visualSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    borderColor: Color = RunkkColors.Border,
    containerColor: Color = RunkkColors.Surface,
    iconTint: Color = RunkkColors.TextPrimary
) {
    val interaction = remember { MutableInteractionSource() }
    val newBadgeDescription = stringResource(R.string.core_buttons_new_badge)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                if (hasBadge) stateDescription = newBadgeDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(visualSize).runkkPressScale(interaction, pressedScale = 0.95f)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
            }
            if (hasBadge) RunkkNotificationDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}

/**
 * 짧게·길게 누름을 나눠 받는 원형 버튼(러닝 종료·잠금 해제, D-49). 짧게 누르면 [onTap](안내만),
 * 길게 누르면 [onLongPress](실제 행동). 보조 기술에는 두 동작이 각각의 라벨로 보인다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RunkkLongPressButton(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    tapLabel: String,
    longPressLabel: String,
    modifier: Modifier = Modifier,
    contentDescription: String = longPressLabel,
    caption: String? = null,
    size: Dp = 96.dp,
    shape: Shape = CircleShape,
    borderColor: Color = RunkkColors.Lavender,
    borderWidth: Dp = 4.dp,
    background: Color = RunkkColors.Surface,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LongPressTarget(
            interaction, onTap, onLongPress, tapLabel, longPressLabel, contentDescription,
            size, shape, borderColor, borderWidth, background, content
        )
        if (caption != null) {
            Text(
                text = caption,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/** 길게 누르는 버튼의 원형 누름 영역. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressTarget(
    interaction: MutableInteractionSource,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    tapLabel: String,
    longPressLabel: String,
    contentDescription: String,
    size: Dp,
    shape: Shape,
    borderColor: Color,
    borderWidth: Dp,
    background: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .runkkPressScale(interaction, pressedScale = 0.94f)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = tapLabel,
                onLongClickLabel = longPressLabel,
                onLongClick = onLongPress,
                onClick = onTap
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 라임 글자 링크("보기", "설정", "바꾸기"). 터치 영역 48dp. */
@Composable
fun RunkkTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription !=
                    null
                ) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .padding(PaddingValues(horizontal = 4.dp)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(text = text, style = RunkkTextStyles.Caption, color = RunkkColors.Lime)
    }
}

private const val DISABLED_ALPHA = 0.4f

/**
 * 비활성 버튼을 흐리게 한다. Modifier.alpha는 1f이면 수정자 자체가 빠져 활성 전환 때 수정자 사슬 구조가 바뀌고,
 * 그 첫 전환에서 버튼이 그려지지 않다가 눌러야 나타나는 결함이 있었다. graphicsLayer는 항상 같은 구조를 유지한다.
 */
private fun Modifier.runkkDisabledAlpha(isEnabled: Boolean): Modifier =
    graphicsLayer { alpha = if (isEnabled) 1f else DISABLED_ALPHA }

@Preview(name = "버튼", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkButtonsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkPrimaryButton("카카오로 시작하기", onClick = {}, modifier = Modifier.fillMaxWidth())
            RunkkPrimaryButton("자유런 시작", onClick = {
            }, modifier = Modifier.fillMaxWidth(), size = RunkkButtonSize.Large, hasTrailingArrow = true)
            RunkkPrimaryButton("다음", onClick = {}, modifier = Modifier.fillMaxWidth(), isEnabled = false)
            RunkkSecondaryButton("Google로 시작하기", onClick = {}, modifier = Modifier.fillMaxWidth())
            RunkkAccentOutlinedButton("회원 탈퇴", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RunkkPillButton("취소", onClick = {})
                RunkkPillButton("카드 공유", onClick = {}, leadingIcon = RunkkIcons.Share)
                RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = {}, hasBadge = true)
                RunkkTextLink("설정", onClick = {})
            }
            RunkkLongPressButton(onTap = {
            }, onLongPress = {}, tapLabel = "종료 안내", longPressLabel = "길게 눌러 종료", caption = "길게 눌러 종료") {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.Lavender))
            }
        }
    }
}

/** 라임 알약 모양(버튼과 표시 알약이 함께 쓴다, 리뷰 AN11-8): 최소 높이 40, 모서리 20, 좌우 여백 16. */
private val LIME_PILL_MIN_HEIGHT = 40.dp
private val LIME_PILL_SHAPE = RoundedCornerShape(20.dp)
private val LIME_PILL_PADDING = 16.dp
