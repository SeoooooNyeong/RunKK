package com.a207.runkk.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/**
 * 메인 4안(FR-243, D-375, 피그마 Q1~Q4): 트랙 그림 배경, 오른쪽에 내 기본 캐릭터 전신 그림, 왼쪽에 세로 평행 카드. 가운데 카드만 크게
 * (아이콘·이름·한 줄 설명) 그리고 위아래 카드는 작게 이름만 그린다. 카드는 자유런·배틀런·셀프런·셀럽런을 순환한다(Q1b~Q4b와 같은 상태).
 * 베젤·크라운 회전과 위아래 넘기기로 한 칸씩 바꾸고, 가운데 카드를 누르면 [onModeSelected]다. 지금 카드 위치는 화면이 갖고
 * 처음 값은 [WatchFlow.Main.mode]다(돌아오면 그 카드).
 */
@Composable
fun WatchModeCarousel(state: WatchFlow.Main, onModeSelected: (WatchMode) -> Unit, modifier: Modifier = Modifier) {
    var mode by remember(state.mode) { mutableStateOf(state.mode) }
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val step = with(density) { STEP_DRAG.toPx() }
    var rotated by remember { mutableFloatStateOf(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TrackBackground(
        isDimmed = false,
        modifier = modifier
            .onRotaryScrollEvent { event ->
                rotated += event.verticalScrollPixels
                if (kotlin.math.abs(rotated) >= step) {
                    mode = if (rotated > 0) mode.next() else mode.previous()
                    rotated = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { dragged = 0f }) { _, amount ->
                    dragged += amount
                    if (kotlin.math.abs(dragged) >= step) {
                        mode = if (dragged < 0) mode.next() else mode.previous()
                        dragged = 0f
                    }
                }
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = maxHeight
            CharacterImage(
                gender = state.gender,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = unit * 0.1f)
                    .height(unit * 0.6f)
            )
            Image(
                painter = painterResource(R.drawable.watch_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = unit * 0.42f, y = -unit * 0.1f)
                    .width(unit * 0.15f)
            )
            SideCard(mode.previous().previous(), unit, centerY = -0.42f, onClick = null)
            SideCard(mode.next().next(), unit, centerY = 0.42f, onClick = null)
            SideCard(mode.previous(), unit, centerY = -0.25f, onClick = { mode = mode.previous() })
            SideCard(mode.next(), unit, centerY = 0.25f, onClick = { mode = mode.next() })
            CenterCard(mode, unit) { onModeSelected(mode) }
        }
    }
}

/** 가운데 큰 카드(라임): 아이콘·이름·한 줄 설명. */
@Composable
private fun CenterCard(mode: WatchMode, unit: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topEnd = unit * 0.1f, bottomEnd = unit * 0.1f)
    val name = modeName(mode)
    val description = stringResource(descriptionOf(mode))
    Box(
        modifier = Modifier
            .offset(y = unit * 0.5f - unit * 0.14f)
            .width(unit * 0.48f)
            .height(unit * 0.28f)
            .background(WatchColors.Lime, shape)
            .border(1.5.dp, Color(0xFFE4F79A), shape)
            .clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .semantics { contentDescription = "$name, $description" }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(start = unit * 0.06f).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ModeIcon(mode, WatchColors.Ink, size = 16.dp)
                Text(
                    text = name,
                    style = labelStyle(color = WatchColors.Ink, size = 19.sp).copy(fontWeight = FontWeight.ExtraBold)
                )
            }
            Text(text = description, style = labelStyle(color = WatchColors.Ink, size = 8.sp))
        }
    }
}

/**
 * 위아래 작은 카드(이름만). [centerY]는 화면 가운데에서의 세로 위치(지름 비율)다. 바깥 카드([onClick]이 null)는 반쯤 가려진 장식이다.
 * 누르는 영역은 보이는 카드보다 크게(높이 [SIDE_TOUCH_HEIGHT]) 잡는다(워치 최소 터치 크기).
 */
@Composable
private fun SideCard(mode: WatchMode, unit: Dp, centerY: Float, onClick: (() -> Unit)?) {
    val isFar = onClick == null
    val height = unit * 0.17f
    val shape = RoundedCornerShape(topEnd = height / 2, bottomEnd = height / 2)
    val name = modeName(mode)
    val touch = maxOf(height, SIDE_TOUCH_HEIGHT)
    val area = Modifier
        .offset(y = unit * (0.5f + centerY) - touch / 2)
        .width(unit * if (isFar) 0.28f else 0.35f)
        .height(touch)
    Box(
        modifier = if (onClick == null) {
            area.clearAndSetSemantics {}
        } else {
            area
                .clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
                .semantics { contentDescription = name }
        },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .alpha(if (isFar) FAR_ALPHA else 1f)
                .background(Brush.horizontalGradient(listOf(SIDE_CARD_START, SIDE_CARD_END)), shape),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (!isFar) {
                Row(
                    modifier = Modifier.padding(end = 10.dp).clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ModeIcon(mode, WatchColors.OnSurface, size = 11.dp)
                    Text(
                        text = name,
                        style = labelStyle(
                            color = WatchColors.OnSurface,
                            size = 11.sp
                        ).copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun descriptionOf(mode: WatchMode): Int = when (mode) {
    WatchMode.FREE -> R.string.mode_free_description
    WatchMode.BATTLE -> R.string.mode_battle_description
    WatchMode.SELF -> R.string.mode_self_description
    WatchMode.CELEB -> R.string.mode_celeb_description
}

/** 한 칸 넘기는 회전·끌기 양. */
private val STEP_DRAG = 36.dp

/** 작은 카드 누르는 영역 높이. */
private val SIDE_TOUCH_HEIGHT = 44.dp

/** 작은 카드 바탕(피그마 Q1 진한 남색 → 회색). */
private val SIDE_CARD_START = Color(0xFF1A2530)
private val SIDE_CARD_END = Color(0xFF2E3236)

/** 바깥 카드 흐림. */
private const val FAR_ALPHA = 0.85f
