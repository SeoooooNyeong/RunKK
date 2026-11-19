package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 점선 테두리(빈 자리·자리표시 카드: "미전송 기록 1건", "[아바타]"). [color]를 주지 않으면 지금 테마의 강한 테두리색이다. */
fun Modifier.runkkDashedBorder(color: Color = Color.Unspecified, corner: Dp = 16.dp, width: Dp = 1.dp): Modifier =
    composed {
        val lineColor = color.takeOrElse { RunkkColors.BorderStrong }
        drawWithContent { drawDashedBorder(lineColor, corner, width) }
    }

private fun ContentDrawScope.drawDashedBorder(color: Color, corner: Dp, width: Dp) {
    drawContent()
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(corner.toPx()),
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )
    )
}

/**
 * 카드(표면, 모서리 20 기본·24 큰 카드). 피그마 카드는 테두리가 없어 기본은 테두리 없이 그린다(S15P31A207-292). 테두리가 필요하면
 * [borderColor]를 넘긴다(예: `RunkkColors.Border`). [onClick]이 있으면 눌리는 카드가 되고 [onClickLabel]을 보조 기술에 알린다.
 * [isDashed]면 점선 테두리(자리표시).
 */
@Composable
fun RunkkCard(
    modifier: Modifier = Modifier,
    shape: Shape = RunkkShapes.Large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    isDashed: Boolean = false,
    borderColor: Color = Color.Transparent,
    background: Color = RunkkColors.Surface,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val corner = when (shape) {
        RunkkShapes.ExtraLarge -> 24.dp
        RunkkShapes.Medium -> 16.dp
        RunkkShapes.Small -> 12.dp
        else -> 20.dp
    }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.runkkPressScale(interaction) else Modifier)
            .clip(shape)
            .background(background)
            .then(
                when {
                    isDashed -> Modifier.runkkDashedBorder(corner = corner)
                    borderColor.alpha > 0f -> Modifier.border(1.dp, borderColor, shape)
                    else -> Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/**
 * 통계 타일: 라벨(12) + 큰 숫자(Barlow Condensed 기울임) + 아래 [caption]. 값과 모양은 [StatTileValue]·[StatTileStyle]로 받는다.
 * [leadingIcon]이 있으면 맨 위 줄에 18 보조색 아이콘을 둔다(아바타 숫자 칸). [label]이 비면 라벨 줄을 그리지 않는다.
 */
@Composable
fun RunkkStatTile(
    label: String,
    value: StatTileValue,
    modifier: Modifier = Modifier,
    caption: String? = null,
    style: StatTileStyle = StatTileStyle(),
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val centered = style.isCentered
    val spacing = if (style.spacing != Dp.Unspecified) {
        style.spacing
    } else if (centered) {
        4.dp
    } else {
        8.dp
    }
    RunkkCard(
        modifier = modifier.then(statTileSemantics(label, value.spoken, onClick)),
        shape = style.shape ?: if (centered) RunkkShapes.Medium else RunkkShapes.Large,
        contentPadding = style.contentPadding
            ?: if (centered) PaddingValues(horizontal = 8.dp, vertical = 16.dp) else PaddingValues(16.dp),
        onClick = onClick,
        borderColor = style.borderColor.takeOrElse { Color.Transparent },
        background = style.background.takeOrElse { RunkkColors.Surface },
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
        val labelText: @Composable () -> Unit = {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = style.labelStyle ?: if (centered) RunkkTextStyles.Micro else RunkkTextStyles.Caption,
                    color = style.labelColor.takeOrElse { RunkkColors.TextSecondary },
                    modifier = Modifier.align(align)
                )
            }
        }
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.align(align).size(STAT_TILE_ICON)
            )
        }
        if (style.labelPosition == StatTileLabelPosition.Top) labelText()
        RunkkNumberText(
            value = value.text,
            unit = value.unit,
            style = style.valueStyle,
            color = style.valueColor.takeOrElse { RunkkColors.TextPrimary },
            modifier = Modifier.align(align)
        )
        if (style.labelPosition == StatTileLabelPosition.Bottom) labelText()
        if (caption != null) {
            Text(
                text = caption,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.align(align)
            )
        }
        footer?.invoke()
    }
}

private val STAT_TILE_ICON: Dp = 18.dp

/** 읽기 문구가 있으면 "라벨 읽기문구"로 읽는다. 누를 수 있는 칸은 버튼 동작을 남기고, 없으면 자식 문구를 합친다. */
private fun statTileSemantics(label: String, spoken: String?, onClick: (() -> Unit)?): Modifier = if (spoken != null) {
    // 읽기 문구로 바꿔도 누를 수 있는 칸(이번 주 목표 → 코치)은 버튼 동작을 남긴다.
    Modifier.clearAndSetSemantics {
        contentDescription = "$label $spoken"
        if (onClick != null) {
            role = Role.Button
            onClick {
                onClick()
                true
            }
        }
    }
} else {
    Modifier.semantics(mergeDescendants = true) {}
}

/** 숫자 + 단위(숫자의 절반 크기). "2.14" + " KM". */
@Composable
fun RunkkNumberText(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    style: TextStyle = RunkkTextStyles.DisplayMedium,
    color: Color = RunkkColors.TextPrimary
) {
    val text = buildAnnotatedString {
        append(value)
        if (unit != null) {
            withStyle(SpanStyle(fontSize = style.fontSize * UNIT_RATIO)) { append(" $unit") }
        }
    }
    Text(text = text, style = style, color = color, modifier = modifier)
}

/**
 * 큰 숫자 표시(러닝 거리 136·결과 104). [caption]은 아래 줄("KILOMETERS").
 */
@Composable
fun RunkkBigNumber(
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    style: TextStyle = RunkkTextStyles.DisplayHuge
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = value, style = style, color = RunkkColors.TextPrimary)
        if (caption != null) {
            Text(
                text = caption,
                style = RunkkTextStyles.display(20, letterSpacingEm = 0.12f),
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/**
 * 진행 막대(높이 8 기본, 가입 단계는 4). [progress]는 0~1. 보조 기술에 진행 비율을 알린다.
 */
@Composable
fun RunkkProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = RunkkColors.Lime,
    trackColor: Color = RunkkColors.Border
) {
    val value = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
    ) {
        if (value > 0f) {
            Box(Modifier.fillMaxWidth(value).height(height).clip(shape).background(color))
        }
    }
}

/** 구간 막대(심박 구간 분포): 비율대로 칸을 나눠 칠한다. */
@Composable
fun RunkkSegmentBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Row(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.filter { it.first > 0f }.forEach { (weight, color) ->
            Box(Modifier.weight(weight).height(height).background(color))
        }
    }
}

/**
 * 강조 배너(라임 배경, "최장 거리 신기록") 또는 안내 배너(어두운 라임, "2km 통과"). 달리기 화면 위에 겹쳐 띄우는 추월·역전 안내는
 * [RunkkGuidanceToast]다.
 */
@Composable
fun RunkkBanner(
    text: String,
    modifier: Modifier = Modifier,
    isStrong: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(if (isStrong) RunkkColors.LimeFill else RunkkColors.LimeContainer)
            .then(if (isStrong) Modifier else Modifier.border(1.dp, RunkkColors.Lime, RunkkShapes.Medium))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = if (isStrong) {
                RunkkTextStyles.text(
                    15,
                    androidx.compose.ui.text.font.FontWeight.Black
                )
            } else {
                RunkkTextStyles.BodyStrong
            },
            color = if (isStrong) RunkkColors.OnLimeFill else RunkkColors.Lime
        )
    }
}

private const val UNIT_RATIO = 0.5f

@Preview(name = "카드·지표", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkCardsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkBanner("최장 거리 신기록 · 직전 4.80km", isStrong = true)
            RunkkBanner("2km 통과 · 11:04 · 평균 5:32")
            RunkkBigNumber("2.14", caption = "KILOMETERS")
            RunkkProgressBar(0.43f)
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
            CenteredTilesPreview()
            RunkkSegmentBar(
                listOf(
                    1f to RunkkColors.BorderStrong,
                    3f to RunkkColors.BorderHover,
                    4f to RunkkColors.LimeDim,
                    2f to RunkkColors.Lime,
                    1f to RunkkColors.Lavender
                )
            )
            RunkkCard(isDashed = true) {
                Text("미전송 기록 1건", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun CenteredTilesPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            "시간",
            StatTileValue("27:48"),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            "평균 페이스",
            StatTileValue("5:32"),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            "평균 심박",
            StatTileValue("152"),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
    }
}
