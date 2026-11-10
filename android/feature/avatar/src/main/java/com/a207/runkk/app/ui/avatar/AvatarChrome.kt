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
import com.a207.runkk.app.core.designsystem.RunkkColors
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
        Text(formatReward(balance), style = RunkkTextStyles.text(14, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/**
 * 아래 고정 단추(피그마 제안: 높이 52, 모서리 26, 아이콘 20 + 간격 6, 글자 16 Bold). [isPrimary]면 라임(primary)에 onPrimary 글자, 아니면
 * 회색(surfaceHigh)에 textPrimary 글자다. [trailing]은 글자 뒤에 붙는다(구매 단추의 코인·가격).
 */
@Composable
internal fun AvatarPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    isPrimary: Boolean = true,
    isEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val background = if (isPrimary) RunkkColors.LimeFill else RunkkColors.SurfaceHigh
    val content = if (isPrimary) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
    Row(
        modifier = modifier
            .heightIn(min = BUTTON_HEIGHT)
            .clip(RoundedCornerShape(BUTTON_HEIGHT / 2))
            .background(background)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(20.dp), tint = content) }
        Text(
            text,
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
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
        Icon(painterResource(R.drawable.avatar_ic_check), null, Modifier.size(14.dp), tint = tint)
    }
}

/** 왼쪽 위 「보유」 칩(textPrimary 바탕, 글자 11 Bold surface 색). */
@Composable
private fun CornerChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = RunkkColors.Surface,
        modifier = modifier
            .padding(7.dp)
            .clip(CircleShape)
            .background(RunkkColors.TextPrimary)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 왼쪽 아래 잠김 칩(textPrimary 85% 바탕, 자물쇠 12 + 조건 숫자 11 Bold). 짧은 조건이 없으면 자물쇠만. */
@Composable
private fun LockChip(item: Shop.Item, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(7.dp)
            .clip(CircleShape)
            .background(RunkkColors.TextPrimary.copy(alpha = LOCK_CHIP_ALPHA))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.avatar_ic_lock), null, Modifier.size(12.dp), tint = RunkkColors.Surface)
        unlockShortLabelRes(item.imageKey)?.let {
            Text(stringResource(it), style = RunkkTextStyles.text(11, FontWeight.Bold), color = RunkkColors.Surface)
        }
    }
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
                painterResource(R.drawable.avatar_ic_plus),
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
        Text(formatReward(price), style = RunkkTextStyles.text(14, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/** 가격 자리 글자(「보유」, 「입는 중」, 13 Medium textSecondary). */
@Composable
internal fun PriceNote(text: String) {
    Text(text, style = RunkkTextStyles.text(13, FontWeight.Medium), color = RunkkColors.TextSecondary)
}

/** 3열 격자 칸 사이 가로 간격(피그마 11). */
internal val GRID_GAP: Dp = 11.dp

private val BUTTON_HEIGHT = 52.dp
private val THUMB = 112.dp
private val THUMB_ART = 94.dp
private val THUMB_RADIUS = 16.dp
private val SHOP_MORE_STROKE = 1.5.dp
private val DASH = 6.dp
private val DASH_GAP = 4.dp
private const val LOCKED_ALPHA = 0.35f
private const val LOCK_CHIP_ALPHA = 0.85f
private const val DISABLED_ALPHA = 0.4f
