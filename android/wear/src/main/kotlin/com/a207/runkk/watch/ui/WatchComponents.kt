package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.watch.R

/** 폰 연결 상태 한 줄(점 + 문구). 연결은 라벤더(실시간 상태), 끊김은 보조색. */
@Composable
internal fun ConnectionStatus(connected: Boolean, label: String, modifier: Modifier = Modifier) {
    val color = if (connected) WatchColors.Lavender else WatchColors.Muted
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = labelStyle(color = color, size = 11.sp))
    }
}

/** 둥근 알약 모양 버튼. 주 버튼은 라임 바탕, 보조 버튼은 테두리만. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val container = if (primary) WatchColors.Lime else Color.Transparent
    val content = if (primary) WatchColors.Background else WatchColors.OnSurface
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) container else WatchColors.Surface)
            .border(1.dp, if (primary) Color.Transparent else WatchColors.Border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = labelStyle(color = if (enabled) content else WatchColors.Muted, size = 15.sp),
            textAlign = TextAlign.Center
        )
    }
}

/** 안내 문구(폰 안내, "폰과 연결 필요"). */
@Composable
internal fun NoticeText(notice: WatchNotice?, modifier: Modifier = Modifier) {
    val text = when (notice) {
        null -> return
        is WatchNotice.FromPhone -> notice.text
        WatchNotice.PhoneNotConnected -> stringResource(R.string.phone_not_connected)
    }
    Text(
        text = text,
        modifier = modifier,
        style = labelStyle(color = WatchColors.Lavender, size = 12.sp),
        textAlign = TextAlign.Center
    )
}
