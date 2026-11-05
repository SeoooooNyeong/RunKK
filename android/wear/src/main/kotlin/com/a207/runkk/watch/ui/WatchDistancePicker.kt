package com.a207.runkk.watch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Distance

/**
 * 거리 카드 고르기(자유·배틀·셀프·셀럽 공용, FR-244, FR-257~FR-259). 화면은 상태 하나를 그리기만 한다.
 * - 자유런·배틀런(피그마 F1·F1-3·B1-1): 가운데 큰 숫자(무제한은 ∞)와 −·+, 둘레 고리, "달리기 시작"·"매칭 시작". 베젤·−·+로 바꾸면
 *   [onDistanceSelected], 시작은 [onStartPressed]다. 서버에 닿지 않으면 "매칭 시작"을 막는다(FR-251).
 * - 셀프런·셀럽런(피그마 S1-3): 거리 목록. 셀프런은 내 최고 기록 시간, 셀럽런은 셀럽 기록 수를 보이고 기록 없는 거리는 "기록 없음"으로
 *   누를 수 없다(W10). 카드를 누르면 [onDistanceSelected]다. 셀프런은 고르지 않은 카드를 고르기만 하고 고른 카드를 다시 누르면 시작한다(W50).
 */
@Composable
fun WatchDistancePicker(
    state: WatchFlow.Picker,
    onDistanceSelected: (Distance?) -> Unit,
    onStartPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is WatchFlow.FreeGoal -> DialPicker(
            state,
            WatchMode.FREE,
            blocked = null,
            onDistanceSelected,
            onStartPressed,
            modifier
        )

        is WatchFlow.BattleDistance ->
            DialPicker(
                state,
                WatchMode.BATTLE,
                blocked = battleBlocked(state),
                onDistanceSelected,
                onStartPressed,
                modifier
            )

        is WatchFlow.SelfDistance -> ListPicker(state, WatchMode.SELF, onDistanceSelected, modifier)

        is WatchFlow.CelebDistance -> ListPicker(state, WatchMode.CELEB, onDistanceSelected, modifier)
    }
}

/** 배틀런 매칭 시작을 막는 사유: 서버에 닿지 않음(FR-251), 랜덤 대결 잠금(FR-080, WR12-1). 없으면 null. */
private fun battleBlocked(state: WatchFlow.BattleDistance): WatchFlow.Notice? = when {
    !state.isOnline -> WatchFlow.Notice.BATTLE_OFFLINE
    state.isRandomLocked -> WatchFlow.Notice.RANDOM_LOCKED
    else -> null
}

/** [blocked]가 있으면 시작 버튼을 막고 다른 안내가 없을 때 그 사유를 보인다. */
@Composable
private fun DialPicker(
    state: WatchFlow.Picker,
    mode: WatchMode,
    blocked: WatchFlow.Notice?,
    onDistanceSelected: (Distance?) -> Unit,
    onStartPressed: () -> Unit,
    modifier: Modifier
) {
    val focus = remember { FocusRequester() }
    val step = with(LocalDensity.current) { ROTARY_STEP.toPx() }
    var rotated by remember { mutableFloatStateOf(0f) }
    val last = state.choices.lastIndex
    val select = { index: Int -> onDistanceSelected(state.choices[index.coerceIn(0, last)].distance) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val current = state.current
    val isUnlimited = current.distance == null
    val spoken = current.distance?.let { distanceLabel(it) } ?: stringResource(R.string.unlimited)
    TrackBackground(
        isDimmed = true,
        modifier = modifier
            .onRotaryScrollEvent { event ->
                rotated += event.verticalScrollPixels
                if (kotlin.math.abs(rotated) >= step) {
                    select(state.selected + if (rotated > 0) 1 else -1)
                    rotated = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable()
    ) {
        // −·+로 바꾸면 고리와 색이 피그마 변형 전환(SMART_ANIMATE 250ms, 넘쳤다 돌아옴)으로 옮겨 간다. 그리기 단계에서만 읽는다.
        val ring by animateFloatAsState(
            if (isUnlimited) 1f else dialFraction(state),
            motionSpec(DIAL_MS, DialEasing),
            label = "dial-ring"
        )
        val ringColor by animateColorAsState(
            if (isUnlimited) WatchColors.Lavender else WatchColors.Lime,
            motionSpec(DIAL_MS, ScreenEasing),
            label = "dial-ring-color"
        )
        EdgeRing(fraction = { ring }, color = { ringColor })
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = modeName(mode),
                modifier = Modifier.background(
                    WatchColors.Dark,
                    RoundedCornerShape(50)
                ).padding(horizontal = 10.dp, vertical = 3.dp),
                style = labelStyle(color = WatchColors.OnSurface, size = 12.sp).copy(fontWeight = FontWeight.Bold)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepButton(
                    symbol = "−",
                    description = stringResource(R.string.distance_decrease),
                    isEnabled = state.selected > 0
                ) { select(state.selected - 1) }
                // 무제한(∞)과 거리 숫자가 같은 칸·같은 크기를 써서 넘겨도 설명 줄과 버튼이 움직이지 않는다.
                Box(
                    modifier = Modifier
                        .size(width = DIAL_NUMBER_WIDTH, height = DIAL_NUMBER_HEIGHT)
                        .semantics { contentDescription = spoken },
                    contentAlignment = Alignment.Center
                ) {
                    DialNumber(current.distance)
                }
                StepButton(
                    symbol = "+",
                    description = stringResource(R.string.distance_increase),
                    isEnabled = state.selected < last
                ) { select(state.selected + 1) }
            }
            // 안내가 있으면 목표 문구 자리에 보인다(작은 원형 화면에서 아래가 잘리지 않게).
            val notice = state.notice ?: blocked
            if (notice != null) {
                FlowNoticeText(notice, modifier = Modifier.padding(horizontal = 30.dp))
            } else {
                Text(
                    text = stringResource(if (isUnlimited) R.string.goal_unlimited else R.string.goal_distance),
                    style = labelStyle(color = WatchColors.OnSurface, size = 10.sp)
                )
            }
            PillButton(
                text = stringResource(if (mode == WatchMode.BATTLE) R.string.match_start else R.string.run_start),
                onClick = onStartPressed,
                modifier = Modifier.padding(top = 4.dp),
                isEnabled = blocked == null,
                textSize = ACTION_TEXT_SIZE
            )
        }
    }
}

/**
 * 다이얼 가운데 숫자(무제한은 라벤더 ∞). 바뀌면 피그마 변형 전환(250ms, 넘쳤다 돌아옴)처럼 누른 쪽에서 새 숫자가 미끄러져 들어오고
 * 옛 숫자는 반대쪽으로 빠진다(+는 오른쪽에서, −는 왼쪽에서).
 */
@Composable
private fun DialNumber(distance: Distance?) {
    val still = LocalMotionStill.current
    AnimatedContent(
        targetState = distance,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (still) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if ((targetState?.meters ?: -1L) > (initialState?.meters ?: -1L)) 1 else -1
                val move = tween<IntOffset>(DIAL_MS, easing = DialEasing)
                val fade = tween<Float>(DIAL_MS, easing = ScreenEasing)
                (slideInHorizontally(move) { direction * it / 2 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(move) { -direction * it / 2 } + fadeOut(fade))
            } using SizeTransform(clip = false)
        },
        label = "dial-number"
    ) { shown ->
        Text(
            text = if (shown == null) "∞" else kmNumber(shown),
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = numberStyle(DIAL_NUMBER_SIZE, if (shown == null) WatchColors.Lavender else WatchColors.Lime)
        )
    }
}

/** −·+ 둥근 버튼(피그마 F1). 끝 카드면 흐리게 두고 누를 수 없다. 터치 영역은 48dp다. */
@Composable
private fun StepButton(symbol: String, description: String, isEnabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TOUCH_SIZE)
            .clip(CircleShape)
            .clickable(enabled = isEnabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(
                30.dp
            ).background(if (isEnabled) WatchColors.Dark else WatchColors.Dark.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = labelStyle(color = if (isEnabled) WatchColors.OnSurface else WatchColors.Muted, size = 18.sp)
            )
        }
    }
}

@Composable
private fun ListPicker(
    state: WatchFlow.Picker,
    mode: WatchMode,
    onDistanceSelected: (Distance?) -> Unit,
    modifier: Modifier
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = state.selected + 1)
    // 안내는 목록 맨 아래라 화면 밖일 수 있다: 생기면 그 자리로 옮겨 보인다(W52 위치 안내는 눌러야 한다).
    LaunchedEffect(state.notice) { if (state.notice != null) listState.animateScrollToItem(state.choices.size + 1) }
    TrackBackground(isDimmed = true, modifier = modifier) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
                // 카드마다 누르는 칸은 48dp지만 보이는 카드는 작아서, 칸끼리 카드 사이 빈자리만큼 겹쳐 피그마 간격(약 38dp)에 맞춘다.
                verticalArrangement = Arrangement.spacedBy(CARD_OVERLAP),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
            ) {
                item {
                    Text(
                        text = stringResource(if (mode == WatchMode.SELF) R.string.self_title else R.string.mode_celeb),
                        modifier = Modifier.padding(bottom = -CARD_OVERLAP),
                        style = labelStyle(color = WatchColors.Lime, size = 17.sp).copy(fontWeight = FontWeight.Black)
                    )
                }
                itemsIndexed(state.choices) { index, choice ->
                    DistanceRow(choice, isHighlighted = index == state.selected, isTwoStep = mode == WatchMode.SELF) {
                        onDistanceSelected(choice.distance)
                    }
                }
                state.notice?.let { notice -> item { FlowNoticeText(notice) } }
            }
        }
    }
}

/**
 * 거리 한 줄(피그마 S1-3): 거리와 오른쪽 값(내 기록 시간·셀럽 기록 수). 가운데(고른) 카드는 라임으로 크게, 바깥 테두리와 왼쪽 ▶를
 * 두르고, 나머지는 작게 그린다. 기록이 없으면 "기록 없음"으로 흐리게 두고 누를 수 없다. 카드는 작아도 누르는 영역은 48dp다.
 */
@Composable
private fun DistanceRow(choice: WatchFlow.Choice, isHighlighted: Boolean, isTwoStep: Boolean, onClick: () -> Unit) {
    val distance = choice.distance ?: return
    val label = distanceLabel(distance)
    val clickLabel = when {
        !isTwoStep -> label
        isHighlighted -> stringResource(R.string.card_start)
        else -> stringResource(R.string.card_select)
    }
    val value = when {
        choice.isOfflineLocked -> stringResource(R.string.notice_celeb_offline)
        !choice.isEnabled -> stringResource(R.string.record_none)
        choice.record != null -> WatchFormat.elapsed(choice.record.seconds)
        choice.count != null -> stringResource(R.string.celeb_count, choice.count)
        else -> ""
    }
    val isLime = isHighlighted && choice.isEnabled
    val textColor = when {
        isLime -> WatchColors.Ink
        choice.isEnabled -> WatchColors.OnSurface
        else -> WatchColors.Muted
    }
    val shape = RoundedCornerShape(if (isHighlighted) 11.dp else 9.dp)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (isHighlighted) SelectArrow(Modifier.align(Alignment.CenterStart).padding(start = ARROW_START))
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isHighlighted) FOCUS_WIDTH else CARD_WIDTH)
                .heightIn(min = TOUCH_SIZE)
                .clip(RoundedCornerShape(FOCUS_CORNER))
                .clickable(enabled = choice.isEnabled, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                .semantics { contentDescription = "$label, $value" },
            contentAlignment = Alignment.Center
        ) {
            if (isLime) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FOCUS_HEIGHT)
                        .border(1.5.dp, WatchColors.Lime.copy(alpha = 0.4f), RoundedCornerShape(FOCUS_CORNER))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(if (isHighlighted) FOCUS_CARD_RATIO else 1f)
                    .height(if (isHighlighted) CARD_HEIGHT_SELECTED else CARD_HEIGHT)
                    .background(if (isLime) WatchColors.Lime else WatchColors.Dark.copy(alpha = 0.6f), shape)
                    .border(1.dp, if (isLime) WatchColors.Lime else WatchColors.Border, shape)
                    .padding(horizontal = if (isHighlighted) 10.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = labelStyle(color = textColor, size = if (isHighlighted) 19.sp else 13.sp)
                        .copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = value,
                    style = labelStyle(color = textColor, size = if (isHighlighted) 14.sp else 11.sp)
                        .copy(fontWeight = if (isHighlighted) FontWeight.Black else FontWeight.Bold),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** 고른 카드 왼쪽 라임 ▶(피그마 S1-3 arrow 22×24px). */
@Composable
private fun SelectArrow(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 11.dp, height = 13.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, WatchColors.Lime)
    }
}

/**
 * 셀프런·셀럽런 거리 카드(피그마 S1-3 300px 화면 ×0.64). 고른 카드는 바깥 테두리(216×72px) 안에 200×58px, 나머지는 186×46px다.
 * 너비는 화면 비율, 높이는 dp다.
 */
private const val FOCUS_WIDTH = 0.72f
private const val FOCUS_CARD_RATIO = 0.93f
private const val CARD_WIDTH = 0.62f
private val FOCUS_HEIGHT = 46.dp
private val FOCUS_CORNER = 15.dp
private val CARD_HEIGHT_SELECTED = 37.dp
private val CARD_HEIGHT = 29.dp
private val ARROW_START = 10.dp
private val CARD_OVERLAP = (-8).dp

/**
 * 거리 고리 채움(피그마 F1-3·F1-5·F1-10·B1-1): 고른 거리를 가장 긴 거리로 나눈 비율이다. 10km가 끝이면 1km는 1/10, 3km는 3/10,
 * 5km는 절반을 채운다.
 */
internal fun dialFraction(state: WatchFlow.Picker): Float {
    val longest = state.choices.maxOf { it.distance?.meters ?: 0L }
    val meters = state.current.distance?.meters ?: return 1f
    return if (longest > 0) meters.toFloat() / longest else 1f
}

/** 셀럽 목록(WatchCelebListScreen) 한 줄 너비(화면 비율)와 높이(워치 최소 터치 크기). */
internal const val ROW_WIDTH = 0.84f
internal val ROW_HEIGHT = 48.dp

/** 워치 최소 터치 크기(Wear OS 권장 48dp). */
internal val TOUCH_SIZE = 48.dp

/** 자유런·배틀런 가운데 큰 숫자(무제한은 ∞) 칸과 글자 크기(피그마 F1-3 120px ×0.64). 칸은 −·+ 자리를 고정한다. */
private val DIAL_NUMBER_WIDTH = 64.dp
private val DIAL_NUMBER_HEIGHT = 64.dp
private val DIAL_NUMBER_SIZE = 77.sp

/** 한 카드 넘기는 회전 양. */
private val ROTARY_STEP = 36.dp
