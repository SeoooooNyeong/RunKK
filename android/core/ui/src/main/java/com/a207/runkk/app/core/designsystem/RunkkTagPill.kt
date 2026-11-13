package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/**
 * 누르지 않는 꼬리표 알약(크루 태그, 기록 태그, 크루장 배지, 주간 배지 등). 색은 [tone]([RunkkTagTone]), 크기는 [size]([RunkkTagSize])로
 * 고르고 [leadingIcon]은 글자 앞 아이콘(글자색)이다. [spoken]이 있으면 보조 기술이 그 문구로 읽는다.
 * 크루 태그 데이터([RunkkTag])와 이름이 겹치지 않게 부품 이름을 RunkkTagPill로 둔다.
 *
 * ```
 * RunkkTagPill(RunkkTags.label(id))                                                   // 카드 안 11 테두리 태그
 * RunkkTagPill("주말 아침", tone = RunkkTagTone.LimeTint, size = RunkkTagSize.Medium)  // 크루 태그 알약
 * ```
 */
@Composable
fun RunkkTagPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: RunkkTagTone = RunkkTagTone.Outline,
    size: RunkkTagSize = RunkkTagSize.XSmall,
    leadingIcon: ImageVector? = null,
    spoken: String? = null
) {
    val shape = RunkkShapes.Pill
    val content = tagContent(tone)
    Row(
        modifier = modifier
            .heightIn(min = size.minHeight)
            .clip(shape)
            .background(tagFill(tone))
            .then(if (tone == RunkkTagTone.Outline) Modifier.border(1.dp, RunkkColors.Border, shape) else Modifier)
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding)
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(size.iconSize))
        }
        Text(label, style = size.textStyle, color = content, maxLines = 1)
    }
}

/** 꼬리표 바탕. */
@Composable
private fun tagFill(tone: RunkkTagTone): Color = when (tone) {
    RunkkTagTone.Outline -> Color.Transparent
    RunkkTagTone.Neutral -> RunkkColors.SurfaceHigh
    RunkkTagTone.Lime -> RunkkColors.LimeFill
    RunkkTagTone.LimeTint -> RunkkColors.TagLime
    RunkkTagTone.PurpleTint -> RunkkColors.TagPurple
    RunkkTagTone.Ink -> RunkkColors.TextPrimary
    RunkkTagTone.Glass -> RunkkColors.Surface.copy(alpha = RunkkAlpha.Glass)
}

/** 꼬리표 글자·아이콘 색. */
@Composable
private fun tagContent(tone: RunkkTagTone): Color = when (tone) {
    RunkkTagTone.Outline, RunkkTagTone.Neutral, RunkkTagTone.Glass -> RunkkColors.TextPrimary
    RunkkTagTone.Lime -> RunkkColors.OnLimeFill
    RunkkTagTone.LimeTint -> RunkkColors.OnTagLime
    RunkkTagTone.PurpleTint -> RunkkColors.OnTagPurple
    RunkkTagTone.Ink -> RunkkColors.Surface
}
