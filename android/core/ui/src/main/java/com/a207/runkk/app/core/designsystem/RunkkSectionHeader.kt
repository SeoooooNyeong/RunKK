package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 구역 머리 한 줄: 제목 + 개수(선택, 4 띄움) + 오른쪽 [trailing](새로고침·편집·기간 토글 등). 제목과 개수는 한 제목으로 합쳐 읽힌다
 * ("크루원 7"). 크기는 [size]([RunkkSectionHeaderSize], 기본 15/18 Bold), 개수 색은 [countColor](기본 보조색, 이벤트 목록은
 * 라벤더)다. 글자는 [titleColor]로 칠한다.
 *
 * ```
 * RunkkSectionHeader("크루원", count = "7") { RunkkTextLink("편집", onClick = onEdit) }
 * ```
 */
@Composable
fun RunkkSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    size: RunkkSectionHeaderSize = RunkkSectionHeaderSize.Medium,
    titleColor: Color = RunkkColors.TextPrimary,
    countColor: Color = RunkkColors.TextSecondary,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = size.titleStyle, color = titleColor)
            if (count != null) Text(count, style = size.countStyle, color = countColor)
        }
        trailing()
    }
}
