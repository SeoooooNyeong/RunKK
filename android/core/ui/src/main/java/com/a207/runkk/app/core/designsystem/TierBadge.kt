package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a207.runkk.app.core.model.TierDisplay

/**
 * 티어 배지 알약(FR-283, 피그마 61354:2910, design-oo 05-21 7.4). 확정 = 티어 바탕·실선 테두리·Bold, 임시 = 표면 바탕·점선 테두리·
 * 엠블럼 45%·「임시 ○○」, 배치 중 = 표면 바탕·Border 실선·회색 원·「배치 중」. 홈 헤더·랭킹은 XSMALL, 마이·랜덤 대결·배치고사는 SMALL.
 * 보조 기술은 「○○ 티어」·「임시 ○○ 티어」·「배치 중」 하나로 읽고 그림은 읽지 않는다(D-506). 글자 크기 설정에 따라 높이가 커진다.
 */
@Composable
fun TierBadge(display: TierDisplay, size: TierBadgeSize, modifier: Modifier = Modifier) {
    val look = badgeLook(display)
    val isProvisional = display is TierDisplay.Provisional
    val borderWidth = if (isProvisional && size == TierBadgeSize.SMALL) PROVISIONAL_SMALL_BORDER else 1.dp
    val minHeight = if (isProvisional && size == TierBadgeSize.SMALL) size.height + 1.dp else size.height
    val outline = if (isProvisional) {
        Modifier.dashedPill(look.border, borderWidth)
    } else {
        Modifier.border(borderWidth, look.border, CircleShape)
    }
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(CircleShape)
            .background(look.container)
            .then(outline)
            .semantics(mergeDescendants = true) { contentDescription = look.spoken }
            .padding(paddingOf(size)),
        horizontalArrangement = Arrangement.spacedBy(if (size == TierBadgeSize.XSMALL) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TierEmblem(look.tier, size.emblem, alpha = if (isProvisional) PROVISIONAL_EMBLEM_ALPHA else 1f)
        Text(
            look.label,
            style = RunkkTextStyles.text(
                size = size.textSize.value.toInt(),
                weight = if (display is TierDisplay.Confirmed) FontWeight.Bold else FontWeight.Medium,
                lineHeight = if (size == TierBadgeSize.XSMALL) XSMALL_LINE_HEIGHT else null
            ),
            color = look.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 티어 배지 크게(D-520, 7.4절 LARGE, 136×145): 큰 엠블럼 112 아래 6 띄우고 티어 이름 18 Bold. 바탕·테두리 없음.
 * 배치고사 완료 축하에 쓴다. Provisional이 오면 배치 중과 같은 그림이다(본인 화면에서 크게 쓰는 곳은 완료 화면뿐이다).
 */
@Composable
fun TierBadgeLarge(display: TierDisplay, modifier: Modifier = Modifier) {
    val look = badgeLook(if (display is TierDisplay.Provisional) TierDisplay.Placing else display)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = look.spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TierEmblem(look.tier, TierEmblemSize.LARGE)
        Text(look.label, style = RunkkTextStyles.text(LARGE_TEXT_SIZE, FontWeight.Bold), color = look.content)
    }
}

@Composable
private fun badgeLook(display: TierDisplay): TierBadgeLook = when (display) {
    is TierDisplay.Confirmed -> {
        val palette = RunkkTierColors.of(display.tier)
        val name = tierName(display.tier) ?: display.tier
        if (palette == RunkkTierColors.of(null)) {
            // 모르는 티어: 이름은 받은 그대로, 색과 그림은 배치 중 것을 쓴다.
            placingLook(name, stringResource(R.string.tier_badge_a11y_confirmed, name))
        } else {
            TierBadgeLook(
                tier = display.tier,
                label = name,
                spoken = stringResource(R.string.tier_badge_a11y_confirmed, name),
                content = palette.content,
                container = palette.container,
                border = palette.content
            )
        }
    }

    is TierDisplay.Provisional -> {
        val palette = RunkkTierColors.of(display.tier)
        val name = tierName(display.tier) ?: display.tier
        TierBadgeLook(
            tier = display.tier,
            label = stringResource(R.string.tier_provisional_format, name),
            spoken = stringResource(R.string.tier_badge_a11y_provisional, name),
            content = palette.content,
            container = RunkkColors.Surface,
            border = palette.content
        )
    }

    TierDisplay.Placing -> placingLook(
        stringResource(R.string.tier_unranked),
        stringResource(R.string.tier_badge_a11y_placing)
    )
}

@Composable
private fun placingLook(label: String, spoken: String): TierBadgeLook = TierBadgeLook(
    tier = null,
    label = label,
    spoken = spoken,
    content = RunkkColors.TextSecondary,
    container = RunkkColors.Surface,
    border = RunkkColors.Border
)

private fun paddingOf(size: TierBadgeSize): PaddingValues = when (size) {
    TierBadgeSize.XSMALL -> PaddingValues(start = 3.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
    TierBadgeSize.SMALL -> PaddingValues(start = 5.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
}

/** 알약 모양 점선 테두리(대시 4·4, 임시 배지). */
private fun Modifier.dashedPill(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val dash = DASH.toPx()
    val inset = stroke / 2
    val radius = (size.height - stroke) / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
    )
}

private val DASH = 4.dp
private val PROVISIONAL_SMALL_BORDER = 1.5.dp
private const val PROVISIONAL_EMBLEM_ALPHA = 0.45f
private const val XSMALL_LINE_HEIGHT = 14
private const val LARGE_TEXT_SIZE = 18
