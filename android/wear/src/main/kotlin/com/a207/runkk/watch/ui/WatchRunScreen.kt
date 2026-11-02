package com.a207.runkk.watch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.domain.run.RunProgress

/**
 * 워치 단독 측정 중(FR-246, FR-254, 피그마 FR∞·FR3·BR1·SR3): 가운데 큰 누적 거리, 그 아래 경과 시간과 현재 페이스, 아래 하트와 심박.
 * 위 줄은 대결이면 거리 차("+5m" 라임, "−6m" 주황), 자유런 목표가 있으면 "목표 3 km", 무제한이면 "LAP n"이다. 둘레 고리는 목표까지의
 * 진행률(무제한은 1km 구간 진행)이다. 안내 문구와 "역전!"은 가운데 띠로 잠깐 띄운다.
 *
 * 조작(피그마에 버튼이 없어 화면 전체로 받는다): 자유런은 누르면 일시정지, 대결은 누르면 "길게 눌러 종료"를 보이고, 모두 1초 이상
 * 누르면 끝낸다(FR-248). 저전력 표시([isAmbient])면 검은 바탕에 거리·시간만 그린다(D-382, 1분마다 다시 그림은 화면 밖이 정한다).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchRunScreen(
    state: WatchFlow.Running,
    onPause: () -> Unit,
    onEndTapped: () -> Unit,
    onEndLongPressed: () -> Unit,
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false
) {
    if (isAmbient) {
        AmbientRun(state.progress, modifier)
        return
    }
    val pauseLabel = stringResource(R.string.run_pause_action)
    val endLabel = stringResource(R.string.run_end_action)
    TrackBackground(
        isDimmed = true,
        modifier = modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = if (state.isPausable) pauseLabel else null,
            onLongClickLabel = endLabel,
            onClick = if (state.isPausable) onPause else onEndTapped,
            onLongClick = onEndLongPressed
        )
    ) {
        val meters = state.progress.distance.meters
        val goal = state.goal
        if (goal != null && goal.meters > 0) {
            EdgeRing(fraction = meters.toFloat() / goal.meters, color = WatchColors.Lime)
        } else {
            EdgeRing(fraction = (meters % LAP_METERS).toFloat() / LAP_METERS, color = WatchColors.Lavender)
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TopLine(state)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = WatchFormat.distanceKm(meters), style = numberStyle(48.sp))
                Text(
                    text = stringResource(R.string.unit_km),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    style = numberStyle(22.sp)
                )
            }
            Box(Modifier.padding(vertical = 4.dp).width(110.dp).height(1.dp).background(WatchColors.Ink))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(WatchFormat.elapsed(state.progress.elapsed.seconds), stringResource(R.string.label_time))
                Metric(
                    WatchFormat.pace(state.progress.currentPace?.secondsPerKm),
                    stringResource(R.string.label_run_pace)
                )
            }
            HeartRate(state.progress, Modifier.padding(top = 8.dp))
        }
        when {
            state.isLeadChanged -> Banner(
                stringResource(R.string.lead_changed),
                leadBannerColor(state)
            )

            state.notice != null -> Banner(stringResource(noticeRes(state.notice)), WatchColors.Lavender)

            state.banner != null -> Banner(state.banner, WatchColors.Lavender)
        }
    }
}

/** 역전 띠 색. 방향을 알면 방향으로(앞질렀으면 라임, 따라잡혔으면 주황), 모르면(단독 측정) 거리 차 부호로 정한다. */
internal fun leadBannerColor(state: WatchFlow.Running): Color = when (state.leadKind) {
    LeadKind.OVERTOOK -> WatchColors.Lime
    LeadKind.OVERTAKEN -> WatchColors.Orange
    null -> if ((state.gapMeters ?: 0) >= 0) WatchColors.Lime else WatchColors.Orange
}

/** 위 줄: 대결 거리 차, 자유런 목표, 무제한 구간 번호. */
@Composable
private fun TopLine(state: WatchFlow.Running) {
    val gap = state.gapMeters
    val goal = state.goal
    when {
        state.mode != WatchMode.FREE && gap != null -> Text(
            text = if (gap >=
                0
            ) {
                stringResource(R.string.battle_gap_ahead, gap)
            } else {
                stringResource(R.string.battle_gap_behind, -gap)
            },
            style = numberStyle(20.sp, if (gap >= 0) WatchColors.Lime else WatchColors.Orange)
        )

        state.mode != WatchMode.FREE -> Text(text = " ", style = numberStyle(20.sp))

        goal != null -> DotLabel(stringResource(R.string.run_goal, kmNumber(goal)))

        else -> DotLabel(stringResource(R.string.run_lap, state.progress.distance.meters / LAP_METERS + 1))
    }
}

/** 라임 점과 작은 글자(피그마 FR3 "● 목표 3 km", FR∞ "● LAP 1"). */
@Composable
private fun DotLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(WatchColors.Lime, CircleShape))
        Text(
            text = text,
            style = labelStyle(color = WatchColors.OnSurface, size = 11.sp).copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(text = value, style = numberStyle(20.sp))
        Text(text = label, style = labelStyle(color = WatchColors.OnSurface, size = 9.sp))
    }
}

/** 하트와 심박. 심박이 없으면 칸을 비운다(FR-246). */
@Composable
internal fun HeartRate(progress: RunProgress, modifier: Modifier = Modifier) {
    val bpm = progress.heartRate?.bpm
    val label = stringResource(R.string.paused_heart_rate, WatchFormat.heartRate(bpm))
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (bpm != null) {
            HeartIcon(WatchColors.Heart, Modifier.clearAndSetSemantics {})
            Text(
                text = bpm.toString(),
                modifier = Modifier.clearAndSetSemantics {
                },
                style = numberStyle(14.sp, WatchColors.Heart)
            )
        }
    }
}

/** 저전력 표시: 검은 바탕, 흰 거리와 시간만(D-382). 번인 방지를 위해 채운 면이 없다. */
@Composable
private fun AmbientRun(progress: RunProgress, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = WatchFormat.distanceKm(progress.distance.meters), style = numberStyle(44.sp, Color.White))
            Text(
                text = stringResource(R.string.unit_km),
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                style = numberStyle(20.sp, Color.White)
            )
        }
        Text(text = WatchFormat.elapsed(progress.elapsed.seconds), style = numberStyle(20.sp, Color.White))
    }
}

/** 무제한 고리 한 바퀴(1km 구간, FR-246 제안). */
private const val LAP_METERS = 1_000L
