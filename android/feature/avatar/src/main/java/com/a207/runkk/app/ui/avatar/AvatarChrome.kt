package com.a207.runkk.app.ui.avatar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkAlpha
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTagPill
import com.a207.runkk.app.core.designsystem.RunkkTagTone
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.avatar.Shop

/** 상단 바 오른쪽 리워드 칩(피그마 제안 「칩 1,240」: 알약, 좌우 12·위아래 8, 코인 16 보라, 숫자 14 Bold). */
@Composable
internal fun BalanceChip(balance: Int?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.avatar_shop_balance_description, balance ?: 0)
    Row(
        modifier = modifier
            .padding(end = 8.dp)
            .clip(CircleShape)
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.avatar_ic_coin), null, Modifier.size(16.dp), tint = RunkkColors.Violet)
        Text(formatReward(balance), style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
    }
}

/**
 * 아래 고정 단추(디자인 시스템 RunkkButton Large 52, 알약, 아이콘 20). [isPrimary]면 라임, 아니면 외곽선(보조 단추)이다. 비활성은 회색 바탕에
 * 보조색 글자다(실기기 DV3-7). [trailing]은 글자 뒤에 붙는다(구매 단추의 코인·가격). 디자인 시스템 단추에는 글자 뒤 칸이 없어 그때만 같은
 * 모양(52, 16 SemiBold, 좌우 16, 간격 8)을 여기서 그린다.
 */
@Composable
internal fun AvatarPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    isPrimary: Boolean = true,
    isEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val style = if (isPrimary) RunkkButtonStyle.Lime else RunkkButtonStyle.Outline
    if (trailing == null) {
        if (icon != null) {
            RunkkButton(text, onClick, icon, modifier, style, isEnabled = isEnabled, iconSize = BUTTON_ICON)
        } else {
            RunkkButton(text, onClick, modifier, style, isEnabled = isEnabled)
        }
        return
    }
    val background = if (isEnabled) RunkkColors.LimeFill else RunkkColors.SurfaceHigh
    val content = if (isEnabled) RunkkColors.OnLimeFill else RunkkColors.TextSecondary
    Row(
        modifier = modifier
            .heightIn(min = RunkkButtonSize.Large.height)
            .clip(RunkkShapes.Pill)
            .background(background)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = RunkkButtonSize.Large.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, Modifier.size(BUTTON_ICON), tint = content) }
        Text(
            text,
            style = RunkkButtonSize.Large.textStyle,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing()
    }
}

/**
 * 아이템 칸(피그마 AV-9: 썸네일 112 정사각, 모서리 16, 바탕 surfaceHigh, 그림 94 가운데 + 아래 이름 13 Medium, 상점이면 그 아래 가격 줄).
 * 상태 표시는 [look]([ItemCellLook]). 잠긴 칸은 이름도 textSecondary다. 읽어 주기는 이름·[stateText] 하나로 묶는다.
 */
@Composable
internal fun ItemCell(
    item: Shop.Item,
    look: ItemCellLook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateText: String? = null,
    priceLine: (@Composable () -> Unit)? = null
) {
    val description = listOfNotNull(item.name, stateText).joinToString(", ")
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = look == ItemCellLook.WEARING || look == ItemCellLook.TRYING
                stateText?.let { stateDescription = it }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ItemThumb(item, look)
        Text(
            item.name,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = if (look == ItemCellLook.LOCKED) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {}
        )
        priceLine?.let { Box(Modifier.clearAndSetSemantics {}) { it() } }
    }
}

@Composable
private fun ItemThumb(item: Shop.Item, look: ItemCellLook) {
    val shape = RoundedCornerShape(THUMB_RADIUS)
    val ring = when (look) {
        ItemCellLook.WEARING -> RunkkColors.LimeFill
        ItemCellLook.TRYING -> RunkkColors.Violet
        else -> null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(THUMB)
            .clip(shape)
            .background(RunkkColors.SurfaceHigh)
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
    ) {
        ItemPicture(
            item,
            Modifier
                .align(Alignment.Center)
                .size(THUMB_ART)
                .alpha(if (look == ItemCellLook.LOCKED) LOCKED_ALPHA else 1f)
        )
        when (look) {
            ItemCellLook.WEARING ->
                CheckBadge(RunkkColors.LimeFill, RunkkColors.OnLimeFill, Modifier.align(Alignment.TopEnd))

            ItemCellLook.TRYING ->
                CheckBadge(RunkkColors.Violet, RunkkColors.OnSecondary, Modifier.align(Alignment.TopEnd))

            ItemCellLook.OWNED ->
                CornerChip(stringResource(R.string.avatar_item_owned_chip), Modifier.align(Alignment.TopStart))

            ItemCellLook.LOCKED -> LockChip(item, Modifier.align(Alignment.BottomStart))

            ItemCellLook.PLAIN -> Unit
        }
    }
}

/** 오른쪽 위 체크(지름 22, 체크 14). */
@Composable
private fun CheckBadge(background: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(RunkkIcons.Check, null, Modifier.size(14.dp), tint = tint)
    }
}

/** 왼쪽 위 「보유」 칩(본문색 바탕에 표면색 글자 11, 디자인 시스템 RunkkTagPill Ink). */
@Composable
private fun CornerChip(text: String, modifier: Modifier = Modifier) {
    RunkkTagPill(text, modifier.padding(7.dp), tone = RunkkTagTone.Ink)
}

/** 왼쪽 아래 잠김 칩(본문색 바탕, 자물쇠 12 + 조건 숫자 11). 짧은 조건이 없으면 자물쇠만. */
@Composable
private fun LockChip(item: Shop.Item, modifier: Modifier = Modifier) {
    RunkkTagPill(
        unlockShortLabelRes(item.imageKey)?.let { stringResource(it) }.orEmpty(),
        modifier.padding(7.dp),
        tone = RunkkTagTone.Ink,
        leadingIcon = RunkkIcons.Lock
    )
}

/** 마지막 칸 「상점에서 더 보기」(점선 테두리 1.5, 가운데 + 28). */
@Composable
internal fun ShopMoreCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.avatar_decorate_more_in_shop)
    val dash = RunkkColors.Border
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(THUMB)
                .clip(RoundedCornerShape(THUMB_RADIUS))
                .background(RunkkColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = SHOP_MORE_STROKE.toPx()
                drawRoundRect(
                    dash,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(THUMB_RADIUS.toPx()),
                    style = Stroke(
                        stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH_GAP.toPx()))
                    )
                )
            }
            Icon(
                RunkkIcons.Plus,
                null,
                Modifier.size(28.dp),
                tint = RunkkColors.TextSecondary
            )
        }
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** 가격 줄(코인 14 보라 + 숫자 14 Bold). */
@Composable
internal fun PriceLine(price: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.avatar_ic_coin), null, Modifier.size(14.dp), tint = RunkkColors.Violet)
        Text(formatReward(price), style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
    }
}

/** 가격 자리 글자(「보유」, 「입는 중」, 13 Medium textSecondary). */
@Composable
internal fun PriceNote(text: String) {
    Text(text, style = RunkkTextStyles.text(13, FontWeight.Medium), color = RunkkColors.TextSecondary)
}

/**
 * 무대 오른쪽 위 되돌리기(디자인 시스템 원형 아이콘 단추: 지름 36, 흰 92% 유리 바탕, 테두리 없음, 아이콘 20). 바꾼 것이 없으면 흐리게
 * 두고 누르지 않는다. 원이 48 누름 칸 오른쪽 위에 붙도록 가운데에서 6만큼 옮긴다.
 */
@Composable
internal fun GlassUndoButton(label: String, isEnabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RunkkIconButton(
        icon = painterResource(R.drawable.avatar_ic_undo),
        contentDescription = label,
        onClick = onClick,
        modifier = modifier.offset(x = UNDO_SHIFT, y = -UNDO_SHIFT),
        visualSize = 36.dp,
        borderColor = Color.Transparent,
        containerColor = RunkkColors.Surface.copy(alpha = RunkkAlpha.Glass),
        isEnabled = isEnabled
    )
}

/** 3열 격자 칸 사이 가로 간격(피그마 11). */
internal val GRID_GAP: Dp = 11.dp

private val BUTTON_ICON = 20.dp
private val UNDO_SHIFT = 6.dp
private val THUMB = 112.dp
private val THUMB_ART = 94.dp
private val THUMB_RADIUS = 16.dp
private val SHOP_MORE_STROKE = 1.5.dp
private val DASH = 6.dp
private val DASH_GAP = 4.dp
private const val LOCKED_ALPHA = 0.35f
