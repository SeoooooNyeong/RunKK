package com.a207.runkk.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.watch.battle.WatchResultHeadline

/**
 * 결과(FR-249, 피그마 FF∞·FG3·FS3·BW1·BL1·BS1·SW3·SL3·SS3): 머리말(FINISH·GOAL·STOP·WIN·LOSE·BEST·GOOD)과 결과 문구, 거리·시간·
 * 평균 페이스·평균 심박, "완료". 머리말·문구는 [WatchResultHeadline]이 낸다. 보류면 머리말 대신 "결과를 확인하고 있어요"다. 배치고사 판이면
 * 머리말은 판정 색 그대로이고 문구가 배치고사 반영이다(워치 배치고사).
 * 평균 심박이 없으면 빈칸이다.
 * 모션(W41): 머리말이 작게 시작해 커지며 나타나고, 값 두 줄과 "완료"가 차례로(60ms 간격) 아래에서 올라온다(320ms).
 */
@Composable
fun WatchResultScreen(state: WatchFlow.Result, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val headline = state.headline
    val summary = state.summary
    val headlineIn = rememberReveal(order = 0)
    val firstRowIn = rememberReveal(order = 1)
    val secondRowIn = rememberReveal(order = 2)
    val doneIn = rememberReveal(order = 3)
    TrackBackground(isDimmed = true, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.revealLayer(headlineIn, fromScale = HEADLINE_START_SCALE).semantics(
                    mergeDescendants = true
                ) {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            ) {
                if (headline.title().isNotEmpty()) {
                    Text(
                        text = headline.title(),
                        style = headlineStyle(RESULT_HEADLINE_SIZE, colorOf(headline))
                    )
                }
                headline.subtitle(resources)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = labelStyle(
                            color = WatchColors.OnSurface,
                            size = if (headline.title().isEmpty()) 13.sp else 10.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp).revealLayer(firstRowIn, rise = REVEAL_RISE),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ResultMetric(WatchFormat.distanceKm(summary.distance.meters), stringResource(R.string.result_distance))
                ResultMetric(WatchFormat.elapsed(summary.elapsed.seconds), stringResource(R.string.result_time))
            }
            Row(
                modifier = Modifier.padding(top = 2.dp).revealLayer(secondRowIn, rise = REVEAL_RISE),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ResultMetric(WatchFormat.pace(summary.averagePace?.secondsPerKm), stringResource(R.string.result_pace))
                // 평균 심박이 없으면 "--" 대신 그 칸을 비운다(FR-249, R13 D5). 평균 페이스 자리는 그대로 둔다.
                val heartRate = summary.averageHeartRate
                if (heartRate != null) {
                    ResultMetric(WatchFormat.heartRate(heartRate), stringResource(R.string.result_heart_rate))
                } else {
                    Spacer(Modifier.width(METRIC_WIDTH))
                }
            }
            PillButton(
                text = stringResource(R.string.result_done),
                onClick = onClose,
                modifier = Modifier.padding(top = 4.dp).revealLayer(doneIn, rise = REVEAL_RISE),
                textSize = ACTION_TEXT_SIZE
            )
        }
    }
}

@Composable
private fun ResultMetric(value: String, label: String) {
    Column(
        modifier = Modifier.width(METRIC_WIDTH).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = numberStyle(RESULT_VALUE_SIZE, weight = FontWeight.Bold))
        Text(text = label, style = labelStyle(color = WatchColors.OnSurface, size = 8.sp))
    }
}

/** 머리말이 나타날 때 처음 크기와 값 줄이 올라오는 거리. */
private const val HEADLINE_START_SCALE = 0.6f
private val REVEAL_RISE = 10.dp

/** 결과 수치 한 칸의 너비. */
private val METRIC_WIDTH = 64.dp

/** 머리말 색: 목표 달성·이김·최고 기록은 라임, 끝냄은 흰색, 중간 종료·짐은 주황, 최고 기록 미달은 라벤더. */
private fun colorOf(headline: WatchResultHeadline): Color = when (headline) {
    WatchResultHeadline.Finish -> WatchColors.OnSurface
    WatchResultHeadline.Goal, is WatchResultHeadline.Win, is WatchResultHeadline.Best -> WatchColors.Lime
    WatchResultHeadline.Stop, is WatchResultHeadline.Lose -> WatchColors.Orange
    is WatchResultHeadline.Good -> WatchColors.Lavender
    WatchResultHeadline.Pending -> WatchColors.OnSurface
    is WatchResultHeadline.Placement -> colorOf(headline.verdict)
}
