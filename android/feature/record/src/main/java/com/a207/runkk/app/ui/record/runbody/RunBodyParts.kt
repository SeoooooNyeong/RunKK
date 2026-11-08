package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/** 런바디 카드(모서리 24, 안쪽 20): 제목(18 굵게) + 내용. */
@Composable
internal fun RunBodyCard(
    title: String?,
    modifier: Modifier = Modifier,
    titleSize: Int = SECTION_TITLE_SIZE,
    content: @Composable ColumnScope.() -> Unit
) {
    RunkkCard(
        modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        title?.let {
            Text(
                it,
                style = RunkkTextStyles.text(titleSize, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }
        content()
    }
}

/** 라벨(왼쪽, 보조색) + 값(오른쪽, 굵게) 한 줄. 보조 기술은 한 줄로 읽는다. */
@Composable
internal fun RunBodyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = RunkkTextStyles.text(17, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/** 보조 문구 한 줄(14, 보조색). */
@Composable
internal fun RunBodyCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary, modifier = modifier)
}

/** 라벤더 원 아이콘(36) + 라벨 + 오른쪽 값: "가장 높은 점수 지구력 84점"(G-13). */
@Composable
internal fun RunBodyIconRow(icon: ImageVector, label: String, value: String, isMuted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.Lavender.copy(alpha = ICON_BACK_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.Violet, modifier = Modifier.size(18.dp))
        }
        Text(label, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = RunkkTextStyles.text(18, FontWeight.Black),
            color = if (isMuted) RunkkColors.TextSecondary else RunkkColors.TextPrimary
        )
    }
}

private const val SECTION_TITLE_SIZE = 18
private const val ICON_BACK_ALPHA = 0.3f
