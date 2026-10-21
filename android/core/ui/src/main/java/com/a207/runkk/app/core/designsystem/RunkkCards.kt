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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 점선 테두리(빈 자리·자리표시 카드: "미전송 기록 1건", "[아바타]"). */
fun Modifier.runkkDashedBorder(
    color: Color = RunkkColors.BorderStrong,
    corner: Dp = 16.dp,
    width: Dp = 1.dp
): Modifier = drawWithContent {
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
 * 카드(표면 + 테두리, 모서리 20 기본·24 큰 카드). [onClick]이 있으면 눌리는 카드가 되고 [onClickLabel]을 보조 기술에 알린다.
 * [dashed]면 점선 테두리(자리표시).
 */
@Composable
fun RunkkCard(
    modifier: Modifier = Modifier,
    shape: Shape = RunkkShapes.Large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    dashed: Boolean = false,
    borderColor: Color = RunkkColors.Border,
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
                if (dashed) Modifier.runkkDashedBorder(corner = corner) else Modifier.border(1.dp, borderColor, shape)
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
 * 통계 타일: 라벨(12) + 큰 숫자(Barlow). [unit]은 숫자 뒤 작은 글자("KM", "%").
 * [centered]면 결과 화면처럼 가운데 정렬(3열 타일).
 */
@Composable
fun RunkkStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = RunkkColors.TextPrimary,
    valueStyle: TextStyle = RunkkTextStyles.DisplayMedium,
    centered: Boolean = false,
    onClick: (() -> Unit)? = null,
    spoken: String? = null,
    footer: (@Composable () -> Unit)? = null
) {
    RunkkCard(
        // spoken이 있으면 "평균 페이스 1킬로미터당 5분 32초"처럼 읽는다("5:32"가 시각으로 읽히지 않게).
        modifier = modifier.then(
            if (spoken != null) {
                Modifier.clearAndSetSemantics { contentDescription = "$label $spoken" }
            } else {
                Modifier.semantics(mergeDescendants = true) {}
            }
        ),
        shape = if (centered) RunkkShapes.Medium else RunkkShapes.Large,
        contentPadding = if (centered) PaddingValues(horizontal = 8.dp, vertical = 16.dp) else PaddingValues(16.dp),
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(if (centered) 4.dp else 8.dp)
    ) {
        val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
        Text(
            text = label,
            style = if (centered) RunkkTextStyles.Micro else RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.align(align)
        )
        RunkkNumberText(
            value = value,
            unit = unit,
            style = valueStyle,
            color = valueColor,
            modifier = Modifier.align(align)
        )
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
 * 큰 숫자 표시(러닝 거리 136·결과 104). [caption]은 아래 줄("KILOMETERS"), [unit]은 옆("KM").
 */
@Composable
fun RunkkBigNumber(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    style: TextStyle = RunkkTextStyles.DisplayHuge,
    color: Color = RunkkColors.TextPrimary
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = value, style = style, color = color)
            if (unit != null) {
                Text(
                    text = unit,
                    style = RunkkTextStyles.display(24),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        if (caption !=
            null
        ) {
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

/** 강조 배너(라임 배경, "최장 거리 신기록") 또는 안내 배너(어두운 라임, "2km 통과"). */
@Composable
fun RunkkBanner(
    text: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(if (strong) RunkkColors.Lime else RunkkColors.LimeContainer)
            .then(if (strong) Modifier else Modifier.border(1.dp, RunkkColors.Lime, RunkkShapes.Medium))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = if (strong) {
                RunkkTextStyles.text(
                    15,
                    androidx.compose.ui.text.font.FontWeight.Black
                )
            } else {
                RunkkTextStyles.BodyStrong
            },
            color = if (strong) RunkkColors.OnLime else RunkkColors.Lime
        )
    }
}

private const val UNIT_RATIO = 0.5f

@Preview(name = "카드·지표", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkCardsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkBanner("최장 거리 신기록 · 직전 4.80km", strong = true)
            RunkkBanner("2km 통과 · 11:04 · 평균 5:32")
            RunkkBigNumber("2.14", caption = "KILOMETERS")
            RunkkProgressBar(0.43f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkStatTile("오늘", "3.02", unit = "KM", caption = "1회", modifier = Modifier.weight(1f))
                RunkkStatTile(
                    "이번 주 목표",
                    "60",
                    unit = "%",
                    valueColor = RunkkColors.Lime,
                    modifier = Modifier.weight(1f)
                ) {
                    RunkkProgressBar(0.6f)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkStatTile(
                    "시간",
                    "27:48",
                    centered = true,
                    valueStyle = RunkkTextStyles.DisplaySmall,
                    modifier = Modifier.weight(1f)
                )
                RunkkStatTile(
                    "평균 페이스",
                    "5:32",
                    centered = true,
                    valueStyle = RunkkTextStyles.DisplaySmall,
                    modifier = Modifier.weight(1f)
                )
                RunkkStatTile(
                    "평균 심박",
                    "152",
                    centered = true,
                    valueStyle = RunkkTextStyles.DisplaySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkSegmentBar(
                listOf(
                    1f to RunkkColors.BorderStrong,
                    3f to RunkkColors.BorderHover,
                    4f to RunkkColors.LimeDim,
                    2f to RunkkColors.Lime,
                    1f to RunkkColors.Lavender
                )
            )
            RunkkCard(dashed = true) {
                Text("미전송 기록 1건", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
    }
}
