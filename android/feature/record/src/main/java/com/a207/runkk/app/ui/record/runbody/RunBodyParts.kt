package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/** 런바디 카드(흰 바탕, 테두리 없음, 모서리 24, 안쪽 20): 제목(18 Bold, 프로필은 20) + 내용. */
@Composable
internal fun RunBodyCard(
    title: String?,
    modifier: Modifier = Modifier,
    titleSize: Int = SECTION_TITLE_SIZE,
    spacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    background: Color = RunkkColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    RunkkCard(
        modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = contentPadding,
        background = background,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        title?.let {
            Text(
                it,
                style = RunkkTextStyles.text(titleSize, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }
        content()
    }
}

/**
 * 라벨(왼쪽, 13 Medium 보조색) + 값(오른쪽, 17 Bold) 한 줄, 글자 기준선 맞춤(피그마 G-20·G-22). 보조 기술은 한 줄로 읽는다. [spokenValue]가
 * 있으면 값 대신 그 문구로 읽는다(페이스 "5′48″" → "1킬로미터당 5분 48초").
 */
@Composable
internal fun RunBodyValueRow(label: String, value: String, modifier: Modifier = Modifier, spokenValue: String? = null) {
    val rowSemantics = if (spokenValue != null) {
        Modifier.clearAndSetSemantics { contentDescription = "$label, $spokenValue" }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(modifier = modifier.fillMaxWidth().then(rowSemantics)) {
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f).alignByBaseline()
        )
        Text(
            value,
            style = RunkkTextStyles.text(17, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

/** 보조 문구 한 줄(14, 보조색). */
@Composable
internal fun RunBodyCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary, modifier = modifier)
}

/**
 * 피드백 줄(높이 42): 보라 컨테이너 원(36) 안 아이콘(20) + 10 + 라벨(13 Medium 보조색) + 오른쪽 값(17 Bold): "가장 높은 점수 지구력 84점"
 * (G-13). [isMuted]면 값을 흐리게(불투명도 0.45, G-13 분석 중).
 */
@Composable
internal fun RunBodyIconRow(icon: ImageVector, label: String, value: String, isMuted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ICON_ROW_HEIGHT).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.SecondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.Violet, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = RunkkTextStyles.text(17, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.alpha(if (isMuted) MUTED_VALUE_ALPHA else 1f)
        )
    }
}

private const val SECTION_TITLE_SIZE = 18
private val ICON_ROW_HEIGHT = 42.dp

/** 분석 중 값("분석 중", "—")의 불투명도(피그마 G-13 분석 중 0.45). */
internal const val MUTED_VALUE_ALPHA = 0.45f
