package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 게시판·소통 화면 공용 조각(와이어프레임 E3·M1~M9): 종류 칩, 작은 단추, ⋯ 메뉴, 작성자 아바타 자리, 종류 꼬리표, 상대 시각.
 * 색은 MaterialTheme에서 읽는다(라이트·다크, NFR-041).
 */
@Composable
internal fun BoardChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(13, if (isSelected) FontWeight.Bold else FontWeight.Normal),
            color = if (isSelected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier
                .clip(shape)
                .background(if (isSelected) colors.primaryContainer else colors.surface)
                .border(1.dp, if (isSelected) colors.primary else colors.outline, shape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** 높이 40의 작은 단추. [isPrimary]면 라임으로 채운다. 터치 영역 48dp. */
@Composable
internal fun BoardButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isEnabled: Boolean = true,
    contentDescription: String? = null,
    isWide: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .then(if (isWide) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = 40.dp)
                .graphicsLayer { alpha = if (isEnabled) 1f else DISABLED_ALPHA }
                .clip(shape)
                .background(if (isPrimary) colors.primary else colors.surface)
                .then(if (isPrimary) Modifier else Modifier.border(1.dp, colors.outline, shape))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = if (isPrimary) colors.onPrimary else colors.onSurface
            )
        }
    }
}

/** 종류 꼬리표("공지", "투표", "공지, 고정"). [isFilled]면 라임으로 채운다. */
@Composable
internal fun KindTag(label: String, isFilled: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = if (isFilled) colors.onPrimary else colors.primary,
        modifier = Modifier
            .clip(shape)
            .background(if (isFilled) colors.primary else colors.primaryContainer)
            .border(1.dp, colors.primary, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** 작성자 아바타 자리(이름 첫 글자). 이름은 옆 글자가 읽으므로 보조 기술에서는 숨긴다. */
@Composable
internal fun AuthorAvatar(name: String, size: Int = 32) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(colors.outlineVariant).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1),
            style = RunkkTextStyles.text(size / 2 - 3, FontWeight.Bold),
            color = colors.onSurfaceVariant
        )
    }
}

/** ⋯ 메뉴 한 줄. */
internal data class MenuEntry(val label: String, val onClick: () -> Unit)

/** ⋯ 단추(터치 영역 48dp)와 그 메뉴. [entries]가 비면 그리지 않는다. */
@Composable
internal fun MoreMenu(description: String, entries: List<MenuEntry>) {
    if (entries.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    var isOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = { isOpen = true })
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            val dot = colors.onSurfaceVariant
            Canvas(Modifier.size(width = 18.dp, height = 4.dp)) {
                val r = size.height / 2
                listOf(r, size.width / 2, size.width - r).forEach { x -> drawCircle(dot, r, Offset(x, r)) }
            }
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = {
            isOpen = false
        }, modifier = Modifier.background(colors.surface)) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label, style = RunkkTextStyles.Body, color = colors.onSurface) },
                    onClick = {
                        isOpen = false
                        entry.onClick()
                    }
                )
            }
        }
    }
}

/**
 * "방금", "25분 전", "2시간 전", "어제", 그보다 앞이면 "10/21". [now]가 정해지지 않았으면(Instant.EPOCH) 날짜와 시각으로 보인다.
 */
@Composable
internal fun relativeLabel(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    if (now == Instant.EPOCH) return postedLabel(at, zone)
    val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
    val days = at.atZone(zone).toLocalDate().until(now.atZone(zone).toLocalDate()).days
    return when {
        minutes < 1 -> stringResource(R.string.crew_time_now)
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.crew_time_minutes, minutes.toInt())
        minutes < MINUTES_PER_DAY -> stringResource(R.string.crew_time_hours, (minutes / MINUTES_PER_HOUR).toInt())
        days == 1 -> stringResource(R.string.crew_time_yesterday)
        else -> postedLabel(at, zone).substringBefore(' ')
    }
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1_440
private const val DISABLED_ALPHA = 0.4f
