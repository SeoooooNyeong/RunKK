package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 진행 화면 왼쪽 위 러닝 종류와 목표 거리 알약(높이 28, 글자 12). */
@Composable
fun RunkkRunLabel(label: String, modifier: Modifier = Modifier) {
    val spoken = RunkkFormat.spokenLabel(label)
    val background = if (LocalRunkkColors.current.isDark) RunkkColors.SurfaceHigh else Color(0xFFE9EDE0)
    Row(
        modifier.height(28.dp).clip(RunkkShapes.Pill).background(background)
            .padding(horizontal = 10.dp)
            .semantics(mergeDescendants = true) { if (spoken != label) contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(RunkkColors.TextPrimary))
        Text(label, style = RunkkTextStyles.text(12, lineHeight = 14), color = RunkkColors.TextPrimary, maxLines = 1)
    }
}
