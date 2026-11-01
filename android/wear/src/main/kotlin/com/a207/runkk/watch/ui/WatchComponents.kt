package com.a207.runkk.watch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/** 한 줄에 두 버튼을 둘 때의 글자 크기. */
internal val ACTION_TEXT_SIZE = 12.sp

/** 폰 연결 상태 한 줄(점 + 문구). 연결은 라벤더(실시간 상태) 채운 점, 끊김은 보조색 빈 점(와이어프레임 K3). */
@Composable
internal fun ConnectionStatus(isConnected: Boolean, label: String, modifier: Modifier = Modifier) {
    val color = if (isConnected) WatchColors.Lavender else WatchColors.Muted
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val dot = Modifier
            .size(6.dp)
            .clip(CircleShape)
        Box(modifier = if (isConnected) dot.background(color) else dot.border(1.dp, color, CircleShape))
        Text(text = label, style = labelStyle(color = color, size = 11.sp))
    }
}

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
        PillTone.STOP -> WatchColors.Surface
    }
    val content = when {
        !isEnabled -> WatchColors.Muted
        tone == PillTone.PRIMARY -> WatchColors.Background
        tone == PillTone.STOP -> WatchColors.Lavender
        else -> WatchColors.OnSurface
    }
    val border = when (tone) {
        PillTone.PRIMARY -> Color.Transparent
        PillTone.OUTLINE -> WatchColors.Border
        PillTone.STOP -> WatchColors.Lavender
    }
    val isCompact = textSize < 15.sp
    // 보이는 알약은 글자가 작으면 40dp로 줄이고, 누르는 영역은 늘 워치 최소 터치 크기 48dp다(WR12-3, RoundIconButton과 같은 방식).
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

/**
 * 아이콘만 있는 원형 테두리 버튼. 작은 원형 화면에서 "길게 눌러 종료" 옆에 글자 버튼이 들어가지 않을 때 쓴다(러닝 화면 일시정지).
 * 글자 대신 [contentDescription]을 읽어 준다.
 */
@Composable
internal fun RoundIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    // 보이는 원은 40dp, 누르는 영역은 워치 최소 터치 크기 48dp다.
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp).border(1.dp, WatchColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) { icon(WatchColors.OnSurface) }
    }
}

/**
 * 러닝·일시정지 화면 아래 한 줄 조작(와이어프레임 K2·K4): 앞 버튼(일시정지 또는 재개, 없을 수 있다)과 "길게 눌러 종료".
 * 종료는 길게 눌러야 폰에 요청하고, 짧게 누르면 [onEndTapped]가 안내만 한다(FR-181).
 */
@Composable
internal fun RunActions(
    onEndTapped: () -> Unit,
    onEndLongPressed: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        PillButton(
            text = stringResource(R.string.hold_to_end),
            onClick = onEndTapped,
            onLongClick = onEndLongPressed,
            tone = PillTone.STOP,
            textSize = ACTION_TEXT_SIZE
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
        WatchNotice.HoldToEnd -> stringResource(R.string.hold_to_end)
    }
    Text(
        text = text,
        modifier = modifier,
        style = labelStyle(color = WatchColors.Lavender, size = 12.sp),
        textAlign = TextAlign.Center
    )
}
