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

/** 알약 버튼 한 벌의 색. */
internal data class PillColors(val container: Color, val content: Color, val border: Color?)

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
 * 알약 버튼(피그마 RunKK / Buttons / 기본, S15P31A207-292). 모양은 [style], 높이·글자는 [size]로 고른다.
 * 꺼지면([isEnabled] 거짓) 회색 채움이 되고 누를 수 없다. [isLoading]이면 글자 대신 도는 표시를 보이고 누름을 막는다.
 * 높이 36·28 버튼도 누르는 칸은 48dp를 보장한다(보이는 알약은 가운데).
 *
 * ```
 * RunkkButton("확인", onClick = onConfirm)                                    // 화면 아래 주 버튼(라임, 52)
 * RunkkButton("취소", onClick = onCancel, style = RunkkButtonStyle.Outline, size = RunkkButtonSize.Medium)
 * ```
 */
@Composable
fun RunkkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RunkkButtonStyle = RunkkButtonStyle.Lime,
    size: RunkkButtonSize = RunkkButtonSize.Large,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = style,
        height = size.height,
        textStyle = size.textStyle,
        horizontalPadding = size.horizontalPadding,
        shape = size.shape,
        isEnabled = isEnabled,
        isLoading = isLoading,
        leadingIcon = leadingIcon
    )
}

/** 알약 버튼 그리기. 높이가 48 미만이면 바깥에 48dp 누름 칸을 두고 알약을 가운데 그린다. */
@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    style: RunkkButtonStyle,
    height: Dp,
    textStyle: TextStyle,
    horizontalPadding: Dp,
    shape: Shape,
    isEnabled: Boolean,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    borderOverride: Color? = null,
    contentOverride: Color? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = style.colors(isEnabled, pressed)
    val content = if (isEnabled) contentOverride ?: colors.content else colors.content
    val border = if (isEnabled) borderOverride ?: colors.border else colors.border
    val loadingDescription = stringResource(R.string.core_buttons_loading)
    val isSmall = height < MIN_TOUCH
    Box(
        modifier = modifier
            .then(if (isSmall) Modifier.minimumInteractiveComponentSize() else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                if (isLoading) stateDescription = loadingDescription
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isEnabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        // 큰 버튼은 부르는 쪽 너비(fillMaxWidth·weight)를 알약까지 넘긴다. 작은 버튼은 48 누름 칸 안에 알약만 그린다.
        propagateMinConstraints = !isSmall
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = height)
                .runkkPressScale(interaction)
                .clip(shape)
                .background(colors.container)
                .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                    color = content,
                    strokeWidth = 3.dp
                )
            } else {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                }
                Text(text = text, style = textStyle, color = content, maxLines = 1)
                trailing?.invoke()
            }
        }
    }
}

/**
 * 기본 CTA(라임 알약, 피그마 Lime Large 52). 화면마다 주 행동 하나에만 쓴다. 새 코드는 [RunkkButton]을 쓴다.
 * [hasTrailingArrow]가 참이면 홈 "자유런 시작"처럼 오른쪽에 검은 원 화살표를 단다([RunkkButtonSize.Hero]와 함께 쓴다).
 */
@Composable
fun RunkkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    size: RunkkButtonSize = RunkkButtonSize.Large,
    hasTrailingArrow: Boolean = false
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = RunkkButtonStyle.Lime,
        height = size.height,
        textStyle = size.textStyle,
        horizontalPadding = size.horizontalPadding,
        shape = size.shape,
        isEnabled = isEnabled,
        isLoading = isLoading,
        trailing = if (hasTrailingArrow) {
            { TrailingArrow() }
        } else {
            null
        }
    )
}

/** 기본 CTA 오른쪽 검은 원 화살표. */
@Composable
private fun TrailingArrow() {
    Box(
        modifier = Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(RunkkColors.OnLimeFill),
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

/** 보조 버튼(피그마 Outline: 흰 채움 + divider 테두리 알약). 주 행동 옆의 다른 선택지에 쓴다. 새 코드는 [RunkkButton]을 쓴다. */
@Composable
fun RunkkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Dp = RunkkButtonSize.Large.height
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = RunkkButtonStyle.Outline,
        height = height,
        textStyle = if (height < RunkkButtonSize.Large.height) {
            RunkkButtonSize.Medium.textStyle
        } else {
            RunkkButtonSize.Large.textStyle
        },
        horizontalPadding = 16.dp,
        shape = RunkkShapes.Pill,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon
    )
}

/** 외곽선 강조 버튼(라벤더 알약): 종료·그만두기·탈퇴처럼 되돌리기 어려운 행동. */
@Composable
fun RunkkAccentOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Dp = RunkkButtonSize.Large.height
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = RunkkButtonStyle.Outline,
        height = height,
        textStyle = RunkkButtonSize.Large.textStyle,
        horizontalPadding = 16.dp,
        shape = RunkkShapes.Pill,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon,
        borderOverride = RunkkColors.Lavender,
        contentOverride = RunkkColors.Lavender
    )
}

/** 작은 외곽선 알약(피그마 Outline Small 36): "취소", "사진 선택", "카드 공유" 같은 작은 행동. 터치 영역은 48dp를 보장한다. */
@Composable
fun RunkkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isEnabled: Boolean = true
) {
    RunkkButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = RunkkButtonStyle.Outline,
        size = RunkkButtonSize.Small,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon
    )
}

/** 작은 라임 알약(피그마 Lime Small 36): 크루 "+ 만들기"·"가입"처럼 작은 주 행동. 터치 영역은 48dp를 보장한다. */
@Composable
fun RunkkLimePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isEnabled: Boolean = true
) {
    RunkkButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = RunkkButtonStyle.Lime,
        size = RunkkButtonSize.Small,
        isEnabled = isEnabled,
        leadingIcon = leadingIcon
    )
}

/**
 * 라임 알약 표시(누르지 않는 값 알약, 홈 리워드 잔액 등): [RunkkLimePillButton]과 같은 모양(최소 높이 36, 알약).
 * 높이를 고정하지 않아 기기 글자 크기를 키우면 알약도 함께 커진다(고정 높이 안에서 글자가 잘리지 않게, 사용자 요청 2026-10-31).
 */
@Composable
fun RunkkLimePill(text: String, modifier: Modifier = Modifier, textStyle: TextStyle = RunkkButtonSize.Small.textStyle) {
    Box(
        modifier = modifier
            .heightIn(min = RunkkButtonSize.Small.height)
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.LimeFill)
            .padding(horizontal = RunkkButtonSize.Small.horizontalPadding),
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

/** 글자 링크("보기", "설정", "바꾸기", 피그마 링크는 본문색 Medium). 터치 영역 48dp. */
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
        Text(
            text = text,
            style = RunkkTextStyles.text(12, FontWeight.Medium, lineHeight = 18),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 누르는 칸 최소 크기(Android 접근성 48dp). */
private val MIN_TOUCH = 48.dp

@Preview(name = "버튼", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkButtonsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkPrimaryButton("카카오로 시작하기", onClick = {}, modifier = Modifier.fillMaxWidth())
            RunkkPrimaryButton("자유런 시작", onClick = {
            }, modifier = Modifier.fillMaxWidth(), size = RunkkButtonSize.Hero, hasTrailingArrow = true)
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
