package com.a207.runkk.watch.ui

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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 *   누를 수 없다(W10). 카드를 누르면 [onDistanceSelected]다.
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
    state.runsLeft != null -> WatchFlow.Notice.RANDOM_LOCKED
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
        EdgeRing(
            fraction = if (isUnlimited) 1f else (state.selected + 1f) / state.choices.size,
            color = if (isUnlimited) WatchColors.Lavender else WatchColors.Lime
        )
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
                    Text(
                        text = if (isUnlimited) "∞" else kmNumber(requireNotNull(current.distance)),
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        style = numberStyle(
                            DIAL_NUMBER_SIZE,
                            if (isUnlimited) WatchColors.Lavender else WatchColors.Lime
                        )
                    )
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
                FlowNoticeText(
                    notice,
                    modifier = Modifier.padding(horizontal = 30.dp),
                    runsLeft = (state as? WatchFlow.BattleDistance)?.runsLeft
                )
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
    TrackBackground(isDimmed = true, modifier = modifier) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
            ) {
                item {
                    Text(
                        text = stringResource(if (mode == WatchMode.SELF) R.string.self_title else R.string.mode_celeb),
                        style = labelStyle(color = WatchColors.Lime, size = 16.sp).copy(fontWeight = FontWeight.Bold)
                    )
                }
                itemsIndexed(state.choices) { index, choice ->
                    DistanceRow(choice, isHighlighted = index == state.selected) { onDistanceSelected(choice.distance) }
                }
                state.notice?.let { notice -> item { FlowNoticeText(notice) } }
            }
        }
    }
}

/** 거리 한 줄(피그마 S1-3): 거리와 오른쪽 값(내 기록 시간·셀럽 기록 수). 기록이 없으면 "기록 없음"으로 흐리게 두고 누를 수 없다. */
@Composable
private fun DistanceRow(choice: WatchFlow.Choice, isHighlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val distance = choice.distance ?: return
    val label = distanceLabel(distance)
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
    Row(
        modifier = Modifier
            .fillMaxWidth(ROW_WIDTH)
            .heightIn(min = ROW_HEIGHT)
            .clip(shape)
            .background(if (isLime) WatchColors.Lime else WatchColors.Dark.copy(alpha = 0.6f), shape)
            .border(1.dp, if (isLime) WatchColors.Lime else WatchColors.Border, shape)
            .clickable(enabled = choice.isEnabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = "$label, $value" }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = labelStyle(color = textColor, size = 15.sp).copy(fontWeight = FontWeight.ExtraBold))
        Text(
            text = value,
            style = labelStyle(color = textColor, size = 12.sp).copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.End
        )
    }
}

/** 목록 한 줄 너비(화면 비율, 피그마 S1-3)와 높이(워치 최소 터치 크기). */
internal const val ROW_WIDTH = 0.84f
internal val ROW_HEIGHT = 48.dp

/** 워치 최소 터치 크기(Wear OS 권장 48dp). */
internal val TOUCH_SIZE = 48.dp

/** 자유런·배틀런 가운데 큰 숫자(무제한은 ∞) 칸과 글자 크기. 가장 넓은 "10"이 들어가는 너비다. */
private val DIAL_NUMBER_WIDTH = 64.dp
private val DIAL_NUMBER_HEIGHT = 64.dp
private val DIAL_NUMBER_SIZE = 60.sp

/** 한 카드 넘기는 회전 양. */
private val ROTARY_STEP = 36.dp
