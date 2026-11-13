package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * 달·주 넘기기 줄(‹ 2026년 10월 ›): 양 끝 화살표와 가운데 [label]. [onPrevious]·[onNext]가 null이면 그쪽 화살표는 흐리게
 * ([RunkkAlpha.DisabledFaint]) 두고 누를 수 없다. [onLabelClick]이 있으면 라벨 뒤에 ⌄(12)를 달고 라벨을 눌러 기간 고르기를 연다
 * ([labelClickLabel]을 보조 기술에 알린다). 화살표 모양은 [style], 라벨 글자는 [labelStyle](기본 16 Bold)이다. 누르는 칸은 48dp다.
 *
 * ```
 * RunkkPeriodNavigator("2026년 10월", onPrevious = ::previous, onNext = if (isLatest) null else ::next)
 * ```
 */
@Composable
fun RunkkPeriodNavigator(
    label: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: RunkkPeriodNavigatorStyle = RunkkPeriodNavigatorStyle.Chevron,
    labelStyle: TextStyle = RunkkTextStyles.CardTitle,
    onLabelClick: (() -> Unit)? = null,
    labelClickLabel: String? = null,
    previousDescription: String = stringResource(R.string.core_period_previous),
    nextDescription: String = stringResource(R.string.core_period_next)
) {
    val (previousIcon, nextIcon) = when (style) {
        RunkkPeriodNavigatorStyle.Arrow -> RunkkIcons.ArrowBack to RunkkIcons.ArrowForward
        else -> RunkkIcons.Back to RunkkIcons.ChevronRight
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PeriodArrow(previousIcon, previousDescription, onPrevious, style)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PeriodLabel(label, labelStyle, onLabelClick, labelClickLabel)
        }
        PeriodArrow(nextIcon, nextDescription, onNext, style)
    }
}

/** 가운데 라벨. 누를 수 있으면 뒤에 ⌄를 단다. */
@Composable
private fun PeriodLabel(label: String, style: TextStyle, onClick: (() -> Unit)?, clickLabel: String?) {
    if (onClick == null) {
        Text(label, style = style, color = RunkkColors.TextPrimary)
        return
    }
    Row(
        modifier = Modifier
            .heightIn(min = TOUCH)
            .clip(RunkkShapes.Small)
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = style, color = RunkkColors.TextPrimary)
        Icon(
            RunkkIcons.ChevronDown,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(12.dp)
        )
    }
}

/** 한쪽 화살표(48 누름 칸). [onClick]이 null이면 흐리게 두고 누를 수 없다. */
@Composable
private fun PeriodArrow(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)?,
    style: RunkkPeriodNavigatorStyle
) {
    if (style == RunkkPeriodNavigatorStyle.Circled) {
        RunkkIconButton(
            icon = icon,
            contentDescription = description,
            onClick = onClick ?: {},
            isEnabled = onClick != null
        )
        return
    }
    Box(
        modifier = Modifier
            .size(TOUCH)
            .clip(CircleShape)
            .clickable(enabled = onClick != null, role = Role.Button, onClick = onClick ?: {})
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier
                .size(ARROW_ICON)
                .alpha(if (onClick != null) 1f else RunkkAlpha.DisabledFaint)
        )
    }
}

private val TOUCH = 48.dp
private val ARROW_ICON = 24.dp
