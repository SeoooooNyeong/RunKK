package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/**
 * 메타 줄의 항목 하나: 앞 아이콘([RunkkIcons.CalendarToday]·[RunkkIcons.Group]·[RunkkIcons.Schedule]·[RunkkIcons.DirectionsRun]·
 * [RunkkIcons.CheckCircle]·[RunkkIcons.EmojiEvents]·[RunkkIcons.TrendingUp]·[RunkkIcons.Person])과 값 글자.
 * [spoken]이 있으면 보조 기술은 [text] 대신 그 문구로 읽는다("5km" → "5킬로미터").
 */
@Immutable
data class MetaItem(val icon: ImageVector, val text: String, val spoken: String? = null)

/**
 * 메타 줄(피그마 제안 62299:1642 "C안 아이콘과 함께", S15P31A207-292): 가운뎃점(·) 대신 항목마다 14 아이콘(보조 글자색) + 4 + 13 Regular
 * 보조 글자를 놓고 항목 사이는 12다. 줄이 넘치면 다음 줄로 넘긴다. 아이콘은 장식이라 읽지 않고, 보조 기술은 항목을 쉼표로 이어
 * 한 번에 읽는다(A안 구현 메모: "5.2킬로미터, 선택됨"처럼 쉼표로 합친다). 문자열을 이어 붙이지 않는다.
 *
 * ```
 * MetaRow(listOf(MetaItem(RunkkIcons.CalendarToday, "2026.10.12 개설"), MetaItem(RunkkIcons.Group, "7명")))
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaRow(items: List<MetaItem>, modifier: Modifier = Modifier) {
    val spoken = items.joinToString(", ") { it.spoken ?: it.text }
    FlowRow(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(META_ITEM_GAP),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(META_ICON_GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(META_ICON_SIZE)
                )
                Text(item.text, style = RunkkTextStyles.Meta, color = RunkkColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

private val META_ICON_SIZE = 14.dp
private val META_ICON_GAP = 4.dp
private val META_ITEM_GAP = 12.dp
