package com.a207.runkk.watch.ui

import android.animation.ValueAnimator
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.Text
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 메인 4안(FR-243, D-375, 피그마 Q1~Q4): 트랙 그림 배경, 오른쪽에 내 기본 캐릭터 전신 그림, 왼쪽에 세로 평행 카드. 가운데 카드만 크게
 * (아이콘·이름·한 줄 설명) 그리고 위아래 카드는 작게 이름만 그린다. 카드는 자유런·배틀런·셀프런·셀럽런을 순환한다(Q1b~Q4b와 같은 상태).
 * 베젤·크라운 회전과 위아래 넘기기로 한 칸씩 바꾸고, 가운데 카드를 누르면 [onModeSelected]다. 지금 카드 위치는 화면이 갖고
 * 처음 값은 [WatchFlow.Main.mode]다(돌아오면 그 카드).
 * 칸을 바꿀 때는 피그마 Smart Animate처럼 카드가 한 칸 미끄러지며 커지고 작아진다(320ms, [CarouselEasing]). 끌기 중에는 손가락을
 * 따라가고 놓으면 가까운 칸으로 붙는다. 저전력 표시([isAmbient])나 애니메이션 끔, 절전 모드면 움직이지 않고 바로 바꾼다.
 */
@Composable
fun WatchModeCarousel(
    state: WatchFlow.Main,
    onModeSelected: (WatchMode) -> Unit,
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false
) {
    var target by remember(state.mode) { mutableIntStateOf(state.mode.ordinal) }
    val position = remember(state.mode) { Animatable(state.mode.ordinal.toFloat()) }
    val isStill = isAmbient || rememberMotionReduced()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val step = with(density) { STEP_DRAG.toPx() }
    var rotated by remember { mutableFloatStateOf(0f) }
    var dragStart by remember { mutableIntStateOf(target) }
    // 끌기 위치는 코루틴 밖에서 바로 더한다(같은 프레임의 이벤트가 앞 델타를 잃지 않게). Animatable에는 이 값을 따라 쓴다.
    var dragPosition by remember { mutableFloatStateOf(position.value) }
    var slotPx by remember { mutableFloatStateOf(1f) }
    val moveTo = { index: Int ->
        target = index
        scope.launch {
            if (isStill) {
                position.snapTo(index.toFloat())
            } else {
                position.animateTo(index.toFloat(), tween(CAROUSEL_DURATION_MS, easing = CarouselEasing))
            }
        }
        Unit
    }
    val release = { moveTo(carouselSettle(dragStart, dragPosition)) }
    // 처음 구성의 release가 아니라 지금 구성의 release를 부르게 한다(isStill·position이 바뀌어도 따라간다).
    val currentRelease by rememberUpdatedState(release)
    LaunchedEffect(Unit) { focus.requestFocus() }
    TrackBackground(
        isDimmed = false,
        modifier = modifier
            .onRotaryScrollEvent { event ->
                rotated += event.verticalScrollPixels
                if (kotlin.math.abs(rotated) >= step) {
                    moveTo(target + if (rotated > 0) 1 else -1)
                    rotated = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragStart = target
                        dragPosition = position.value
                        scope.launch { position.stop() }
                    },
                    onDragEnd = { currentRelease() },
                    onDragCancel = { currentRelease() }
                ) { change, amount ->
                    change.consume()
                    dragPosition -= amount / slotPx
                    scope.launch { position.snapTo(dragPosition) }
                }
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = maxHeight
            slotPx = with(density) { (unit * SLOT_GAP).toPx() }
            CharacterImage(
                gender = state.gender,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = unit * 0.1f)
                    .height(unit * 0.6f)
            )
            // 위치는 카드 안에서 읽는다. 여기서는 어느 번호의 카드가 있는지(칸이 바뀔 때만)만 읽어 매 프레임 다시 구성하지 않는다.
            val base by remember(position) { derivedStateOf { kotlin.math.floor(position.value).toInt() } }
            (base - VISIBLE_SLOTS..base + VISIBLE_SLOTS + 1).forEach { index ->
                key(index) {
                    PositionedModeCard(index, position, unit, onModeSelected, moveTo)
                }
            }
            // 메인으로 돌아오며 받은 안내(측정 중단, 거리 0이라 저장 안 함)는 카드 위에 잠깐 띄운다(R13 D4).
            state.notice?.let { notice ->
                Banner(stringResource(noticeRes(notice)), WatchColors.Lavender, Modifier.zIndex(NOTICE_Z))
            }
        }
    }
}

/**
 * [index]번 카드 하나. 위치([position])를 이 카드 안에서 읽어 프레임마다 이 카드만 다시 구성하고, 가까운 카드가 위에 오도록 그리기
 * 순서(zIndex)를 배치 단계에서 정한다. 가운데는 선택, 한 칸 옆은 그 칸으로 이동, 그 밖은 장식이다.
 */
@Composable
private fun PositionedModeCard(
    index: Int,
    position: Animatable<Float, AnimationVector1D>,
    unit: Dp,
    onModeSelected: (WatchMode) -> Unit,
    moveTo: (Int) -> Unit
) {
    val distance = index - position.value
    if (kotlin.math.abs(distance) >= VISIBLE_SLOTS + 1) return
    val mode = carouselMode(index)
    val onClick: (() -> Unit)? = when (kotlin.math.abs(distance).roundToInt()) {
        0 -> ({ onModeSelected(mode) })
        1 -> ({ moveTo(index) })
        else -> null
    }
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0, zIndex = -kotlin.math.abs(index - position.value))
            }
        }
    ) {
        ModeCard(mode, carouselSlot(distance), unit, onClick)
    }
}

/**
 * 카드 한 장. [slot]의 강조가 1이면 가운데 큰 카드(라임, 아이콘·이름·한 줄 설명), 0이면 위아래 작은 카드(이름만)이고 그 사이는 섞어
 * 그린다. 누르는 영역은 보이는 카드보다 크게(높이 [SIDE_TOUCH_HEIGHT] 이상) 잡는다(워치 최소 터치 크기). [onClick]이 null인 바깥
 * 카드는 반쯤 가려진 장식이다.
 */
@Composable
private fun ModeCard(mode: WatchMode, slot: CarouselSlot, unit: Dp, onClick: (() -> Unit)?) {
    val e = slot.emphasis
    val height = unit * slot.height
    val touch = maxOf(height, SIDE_TOUCH_HEIGHT)
    val corner = lerp(height / 2, unit * 0.1f, e)
    val shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner)
    val name = modeName(mode)
    val description = stringResource(descriptionOf(mode))
    val isCenter = e > 0.5f
    val area = Modifier
        .offset(y = unit * (0.5f + slot.centerY) - touch / 2)
        .width(unit * slot.width)
        .height(touch)
    val ink = lerp(WatchColors.OnSurface, WatchColors.Ink, e)
    Box(
        modifier = when {
            onClick == null -> area.clearAndSetSemantics {}

            isCenter ->
                area
                    .clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
                    .semantics { contentDescription = "$name, $description" }

            else ->
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
                .alpha(slot.alpha)
                // 커지고 작아지는 동안 글자가 카드 밖으로 나오지 않게 자른다(멈춘 카드는 그대로 그린다).
                .then(if (e > 0f && e < 1f) Modifier.clip(shape) else Modifier)
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(SIDE_CARD_START, WatchColors.Lime, e), lerp(SIDE_CARD_END, WatchColors.Lime, e))
                    ),
                    shape
                )
                .then(if (e > 0f) Modifier.border(1.5.dp, CENTER_BORDER.copy(alpha = e), shape) else Modifier),
            contentAlignment = BiasAlignment(horizontalBias = 1f - e, verticalBias = 0f)
        ) {
            if (slot.labelAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .padding(start = unit * 0.06f * e, end = 10.dp * (1f - e))
                        .alpha(slot.labelAlpha)
                        .clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(lerp(3.dp, 4.dp, e))
                    ) {
                        ModeIcon(mode, ink, size = lerp(11.dp, 16.dp, e))
                        Text(
                            text = name,
                            softWrap = false,
                            maxLines = 1,
                            style = labelStyle(color = ink, size = (11f + 8f * e).sp).copy(
                                fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Bold
                            )
                        )
                    }
                    if (e > 0f) {
                        Text(
                            text = description,
                            modifier = Modifier.alpha(((e - 0.5f) * 2f).coerceIn(0f, 1f)).revealHeight(e),
                            softWrap = false,
                            maxLines = 1,
                            style = labelStyle(color = WatchColors.Ink, size = 8.sp)
                        )
                    }
                }
            }
        }
    }
}

/** 설명 줄이 차지하는 높이를 [fraction]만큼만 잡아 카드가 커지는 동안 이름이 부드럽게 올라가게 한다(글자는 뒤 절반에서 나타난다). */
private fun Modifier.revealHeight(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * fraction).roundToInt()) { placeable.place(0, 0) }
}

/** 시스템 애니메이션이 꺼졌거나(접근성 애니메이션 삭제, 개발자 옵션 배율 0) 절전 모드면 움직임을 줄인다. */
@Composable
private fun rememberMotionReduced(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        !ValueAnimator.areAnimatorsEnabled() ||
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
}

private fun descriptionOf(mode: WatchMode): Int = when (mode) {
    WatchMode.FREE -> R.string.mode_free_description
    WatchMode.BATTLE -> R.string.mode_battle_description
    WatchMode.SELF -> R.string.mode_self_description
    WatchMode.CELEB -> R.string.mode_celeb_description
}

/** 한 칸 넘기는 회전 양. */
private val STEP_DRAG = 36.dp

/** 가운데 카드와 다음 카드 사이(지름 비율). 끌기 이만큼이 한 칸이다. */
private const val SLOT_GAP = 0.25f

/** 가운데에서 이만큼(칸) 떨어진 카드까지 그린다. */
private const val VISIBLE_SLOTS = 2

/** 안내 띠는 카드(zIndex 0 이하)보다 위에 그린다. */
private const val NOTICE_Z = 1f

/** 가운데 카드 테두리(피그마 Q1). */
private val CENTER_BORDER = Color(0xFFE4F79A)

/** 작은 카드 누르는 영역 높이. */
private val SIDE_TOUCH_HEIGHT = TOUCH_SIZE

/** 작은 카드 바탕(피그마 Q1 진한 남색 → 회색). */
private val SIDE_CARD_START = Color(0xFF1A2530)
private val SIDE_CARD_END = Color(0xFF2E3236)
