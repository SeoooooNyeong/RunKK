package com.a207.runkk.watch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/** 한 줄에 두 버튼을 둘 때의 글자 크기. */
internal val ACTION_TEXT_SIZE = 12.sp

/**
 * 둥근 알약 모양 버튼. 색은 [tone]([PillTone]), [icon]이 있으면 글자 앞에 둔다(글자색을 받는다).
 * [onLongClick]이 있으면 길게 누르기도 받는다. 한 줄에 두 개를 둘 때는 [textSize]를 [ACTION_TEXT_SIZE]로 줄인다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.PRIMARY,
    isEnabled: Boolean = true,
    textSize: TextUnit = 15.sp,
    onLongClick: (() -> Unit)? = null,
    icon: (@Composable (Color) -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val container = when (tone) {
        PillTone.PRIMARY -> WatchColors.Lime
        PillTone.OUTLINE -> Color.Transparent
    }
    val content = when {
        !isEnabled -> WatchColors.Muted
        tone == PillTone.PRIMARY -> WatchColors.Background
        else -> WatchColors.OnSurface
    }
    val border = when (tone) {
        PillTone.PRIMARY -> Color.Transparent
        PillTone.OUTLINE -> WatchColors.Border
    }
    val isCompact = textSize < 15.sp
    // 보이는 알약은 글자가 작으면 40dp로 줄이고, 누르는 영역은 늘 워치 최소 터치 크기 48dp다(WR12-3).
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .combinedClickable(enabled = isEnabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (isCompact) 40.dp else 48.dp)
                .clip(shape)
                .background(if (isEnabled) container else WatchColors.Surface)
                .border(1.dp, border, shape)
                .padding(horizontal = if (isCompact) 12.dp else 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            icon?.invoke(content)
            Text(text = text, style = labelStyle(color = content, size = textSize), textAlign = TextAlign.Center)
        }
    }
}
