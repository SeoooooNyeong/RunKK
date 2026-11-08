package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/** 크루 화면 공용 조각: 고르는 칩(주간 공동 목표 M8), ⋯ 메뉴(크루 홈 머리글 F-17·F-24). 색은 MaterialTheme에서 읽는다(NFR-041). */
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
