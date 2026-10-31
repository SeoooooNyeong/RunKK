package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * 대결 근접 알림 화면(와이어프레임 K5, FR-196): 근접 알림이 켜진 동안 러닝 화면 대신 보인다.
 * 경과 시간, 큰 격차("+18m" 앞섬 라임, "-18m" 뒤처짐 라벤더), 앞섬·뒤처짐, 근접 알림 간격과 세기 점, 내 거리.
 * 상대 표시명·대결 거리·상대 거리는 워치 계약(wear.contract)에 없어 보이지 않는다. 대결은 폰에서 끝내므로 버튼이 없다.
 */
@Composable
fun WatchBattleScreen(state: WatchUiState, proximity: WatchProximity, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = GAP_ITEM_INDEX)
    val accent = if (proximity.isAhead) WatchColors.Lime else WatchColors.Lavender
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState)
        ) {
            item {
                Text(text = WatchFormat.elapsed(state.elapsedSeconds), style = numberStyle(16.sp, WatchColors.Muted))
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            if (proximity.isAhead) R.string.battle_gap_ahead else R.string.battle_gap_behind,
                            proximity.gapMeters
                        ),
                        style = numberStyle(46.sp, accent)
                    )
                    Text(
                        text = stringResource(if (proximity.isAhead) R.string.battle_ahead else R.string.battle_behind),
                        style = labelStyle(
                            color = WatchColors.OnSurface,
                            size = 13.sp
                        ).copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            item { ProximityPanel(proximity.intervalMillis) }
            item {
                Text(
                    text = stringResource(R.string.battle_my_distance, WatchFormat.distanceKm(state.distanceMeters)),
                    style = labelStyle(size = 11.sp)
                )
            }
            state.notice?.let { notice -> item { NoticeText(notice) } }
        }
    }
}

/** 근접 알림 간격과 세기 점(가까울수록 많이 찬다). */
@Composable
private fun ProximityPanel(intervalMillis: Long) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(WatchColors.Surface, shape)
            .border(1.dp, WatchColors.Border, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SoundIcon(color = WatchColors.Lavender)
            Text(
                text = stringResource(R.string.battle_proximity_interval, WatchFormat.intervalSeconds(intervalMillis)),
                style = labelStyle(color = WatchColors.OnSurface, size = 11.sp)
            )
        }
        val level = WatchFormat.proximityLevel(intervalMillis)
        Row(
            modifier = Modifier.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(WatchFormat.PROXIMITY_LEVELS) { index ->
                // 오른쪽부터 채운다(와이어프레임 K5).
                val isFilled = index >= WatchFormat.PROXIMITY_LEVELS - level
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(if (isFilled) WatchColors.Lavender else WatchColors.Border, CircleShape)
                )
            }
        }
    }
}

/** 처음 가운데 둘 항목(격차). */
private const val GAP_ITEM_INDEX = 1

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchBattleScreenPreview() {
    WatchTheme {
        WatchRoot(
            flow = WatchFlow.Companion,
            companion = WatchUiState(
                isPhoneConnected = true,
                isRunning = true,
                elapsedSeconds = 940,
                distanceMeters = 2_840,
                heartRateBpm = 160,
                proximity = WatchProximity(gapMeters = 18, isAhead = true, intervalMillis = 800)
            ),
            actions = WatchActions.NONE
        )
    }
}
