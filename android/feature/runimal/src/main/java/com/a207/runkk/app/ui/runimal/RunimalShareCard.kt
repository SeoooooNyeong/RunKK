package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.runimal.RunimalCard

/**
 * Runimal 공유 카드(피그마 K-3 "K / Runimal 카드"): 위에 "MY Runimal" 라임 꼬리표와 임시 유형이면 "임시 유형" 테두리 꼬리표,
 * 가운데 유형 그림(120)·이름·한 줄 설명, 아래 성향 막대 넷(우세 쪽 이름 굵게, 진한 채움). 공유 단계가 이 카드를 그대로 찍어 보낸다.
 * 색은 테마 변수라 다크에서도 그대로 바뀐다. 서버가 축 막대를 보내지 않는 옛 서버면 축 비율 막대를 쓴다.
 */
@Composable
internal fun RunimalShareCard(card: RunimalCard, isProvisional: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallTag(stringResource(R.string.runimal_title), isLime = true)
            if (isProvisional) SmallTag(stringResource(R.string.runimal_type_provisional), isLime = false)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val art = RunimalArt.of(card.type)
            RunimalArtTile(
                art,
                size = ART_SIZE,
                contentDescription = if (art == RunimalArt.UNKNOWN) card.title else stringResource(art.nameRes)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {}
            ) {
                Text(
                    card.title,
                    style = RunkkTextStyles.text(22, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    card.description,
                    style = RunkkTextStyles.text(13),
                    color = RunkkColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (card.axes.isNotEmpty()) {
                card.axes.forEach { LeanBar(it, LeanBarStyle.SHARE_CARD) }
            } else {
                card.axisRatios.entries.forEachIndexed { index, (axis, ratio) ->
                    AxisBar(axis, ratio, isLime = index % 2 == 0)
                }
            }
        }
    }
}

/** 작은 꼬리표(높이 17 남짓, 둥근 알약, 글자 11): 라임 채움 또는 테두리. */
@Composable
private fun SmallTag(label: String, isLime: Boolean) {
    val base = Modifier.clip(CircleShape)
    Text(
        label,
        style = RunkkTextStyles.text(11),
        color = if (isLime) RunkkColors.OnLimeFill else RunkkColors.TextPrimary,
        modifier = (
            if (isLime) {
                base.background(RunkkColors.LimeFill)
            } else {
                base.border(1.dp, RunkkColors.Border, CircleShape)
            }
            ).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private val ART_SIZE = 120.dp
