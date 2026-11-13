package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 안내 카드: 옅은 바탕 상자에 제목(선택, 14 Bold) + 본문(13/20 보조색) + 행동 알약(선택, 외곽선 36). 앞 [icon]은 22 아이콘이다.
 * 바탕은 [tone]([RunkkNoticeTone]), 모양은 [shape](기본 모서리 16), 안쪽은 [contentPadding](기본 16×12)이다.
 * [isCentered]면 글자와 행동을 가운데 둔다(배치고사 결과 안내). [isLive]면 문구가 나타나거나 바뀔 때 보조 기술이 읽는다.
 *
 * ```
 * RunkkNoticeCard("결과를 정리하고 있어요", isCentered = true, isLive = true, actionLabel = "다시 확인", onAction = onRetry)
 * ```
 */
@Composable
fun RunkkNoticeCard(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: RunkkNoticeTone = RunkkNoticeTone.Surface,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isCentered: Boolean = false,
    isLive: Boolean = false,
    shape: Shape = RunkkShapes.Medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
    val align = if (isCentered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(noticeFill(tone))
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = noticeIconTint(tone), modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (isLive) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            horizontalAlignment = align,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (title != null) {
                Text(
                    title,
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextPrimary,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                body,
                style = RunkkTextStyles.Meta,
                color = RunkkColors.TextSecondary,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
            if (actionLabel != null && onAction != null) {
                RunkkPillButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 안내 카드 바탕. */
@Composable
private fun noticeFill(tone: RunkkNoticeTone): Color = when (tone) {
    RunkkNoticeTone.Surface -> RunkkColors.Surface
    RunkkNoticeTone.SurfaceHigh -> RunkkColors.SurfaceHigh
    RunkkNoticeTone.Violet -> RunkkColors.SecondaryContainer
    RunkkNoticeTone.Accent -> RunkkColors.AccentContainer
}

/** 안내 카드 앞 아이콘 색. */
@Composable
private fun noticeIconTint(tone: RunkkNoticeTone): Color = when (tone) {
    RunkkNoticeTone.Violet -> RunkkColors.Violet
    RunkkNoticeTone.Accent -> RunkkColors.Lavender
    else -> RunkkColors.TextSecondary
}
