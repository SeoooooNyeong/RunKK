package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.a207.runkk.watch.battle.RecordOpponent

/**
 * 셀럽 기록 목록(FR-259, W09). 셀프런 거리 목록과 같은 틀이다(W03, 피그마 S1-3). 항목은 셀럽 표시명(가림 이름), 완주 시간, 출처다.
 * 항목을 누르면 [onCelebritySelected]로 카운트다운에 간다. 구간 기록을 받는 동안(W31) 그 항목은 완주 시간 대신 "받는 중"이다.
 */
@Composable
fun WatchCelebListScreen(
    state: WatchFlow.CelebList,
    onCelebritySelected: (RecordOpponent) -> Unit,
    modifier: Modifier = Modifier
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
                        text = distanceLabel(state.distance),
                        style = labelStyle(color = WatchColors.Lime, size = 16.sp).copy(fontWeight = FontWeight.Bold)
                    )
                }
                itemsIndexed(state.celebrities) { index, celebrity ->
                    CelebRow(
                        record = celebrity,
                        isHighlighted = index == state.selected,
                        isFetching = celebrity == state.fetching
                    ) { onCelebritySelected(celebrity) }
                }
                state.notice?.let { notice -> item { FlowNoticeText(notice) } }
            }
        }
    }
}

@Composable
private fun CelebRow(record: RecordOpponent, isHighlighted: Boolean, isFetching: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val time = if (isFetching) {
        stringResource(
            R.string.celeb_fetching
        )
    } else {
        WatchFormat.elapsed(record.finishTime.seconds)
    }
    val ink = if (isHighlighted) WatchColors.Ink else WatchColors.OnSurface
    Row(
        modifier = Modifier
            .fillMaxWidth(ROW_WIDTH)
            .heightIn(min = ROW_HEIGHT)
            .clip(shape)
            .background(if (isHighlighted) WatchColors.Lime else WatchColors.Dark.copy(alpha = 0.6f), shape)
            .border(1.dp, if (isHighlighted) WatchColors.Lime else WatchColors.Border, shape)
            .clickable(role = Role.Button, onClickLabel = record.displayName, onClick = onClick)
            .semantics {
                contentDescription =
                    listOfNotNull(record.displayName, time, record.sourceLabel).joinToString(", ")
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(
                text = record.displayName,
                style = labelStyle(color = ink, size = 14.sp).copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            record.sourceLabel?.let {
                Text(
                    text = it,
                    style = labelStyle(color = if (isHighlighted) WatchColors.Ink else WatchColors.Muted, size = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = time,
            modifier = Modifier.clearAndSetSemantics {},
            style = labelStyle(color = ink, size = 12.sp).copy(fontWeight = FontWeight.Bold)
        )
    }
}
