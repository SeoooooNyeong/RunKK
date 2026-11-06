package com.a207.runkk.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Gender

/**
 * 매칭 완료(FR-086, FR-233, 피그마 BMB): 티어와 거리, 나와 상대 캐릭터 얼굴(성별로 고른다), "VS", "매칭 상대를 찾았어요!".
 * 출발 3초 전에 카운트다운으로 바뀐다(조정자 출발 예약). 배치고사 대결이면(워치 배치고사, FR-276) 가상 상대를 숨긴다: 얼굴 대신 점선 원
 * 「?」, 이름 대신 「배치고사 상대」, 티어 칩 자리에 「배치고사 n/3」, 문구는 「배치고사를 시작해요!」다.
 * 모션(W41): 매칭 중 화면 위로 겹쳐 나타난 뒤(250ms) 내 얼굴은 왼쪽에서, 상대 얼굴은 오른쪽에서 들어오고 "VS"가 커지며 나타나고
 * 마지막에 문구가 올라온다(320ms, 60ms 간격).
 */
@Composable
fun WatchMatchedScreen(state: WatchFlow.Matched, modifier: Modifier = Modifier) {
    val facesIn = rememberReveal(order = 0)
    val versusIn = rememberReveal(order = 1)
    val messageIn = rememberReveal(order = 2)
    TrackBackground(isDimmed = true, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            val round = state.placementRound
            MatchHeader(state.tier, state.distance, label = round?.let { placementRoundText(it) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Runner(
                    stringResource(R.string.matched_me),
                    state.myGender,
                    WatchColors.Lavender,
                    MY_FACE_FILL,
                    Modifier.revealLayer(facesIn, shift = -FACE_SHIFT)
                )
                Text(
                    text = stringResource(R.string.matched_vs),
                    modifier = Modifier.revealLayer(versusIn, fromScale = VERSUS_START_SCALE),
                    style = numberStyle(22.sp, WatchColors.Lime)
                )
                if (state.isOpponentHidden) {
                    HiddenRunner(
                        stringResource(R.string.placement_opponent),
                        Modifier.revealLayer(facesIn, shift = FACE_SHIFT)
                    )
                } else {
                    Runner(
                        state.opponentName,
                        state.opponentGender,
                        WatchColors.Orange,
                        WatchColors.Orange,
                        Modifier.revealLayer(facesIn, shift = FACE_SHIFT)
                    )
                }
            }
            Text(
                text = stringResource(if (state.isOpponentHidden) R.string.placement_matched else R.string.matched),
                modifier = Modifier.padding(top = 4.dp).revealLayer(messageIn, rise = MESSAGE_RISE),
                style = labelStyle(color = WatchColors.OnSurface, size = 14.sp).copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/** 얼굴 원과 이름. */
@Composable
private fun Runner(name: String, gender: Gender?, ring: Color, fill: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FaceImage(
            gender = gender,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(fill, CircleShape)
                .border(2.dp, ring, CircleShape)
                .padding(top = 4.dp)
        )
        RunnerName(name)
    }
}

/** 숨긴 상대(배치고사 가상 상대, FR-276): 얼굴 대신 점선 원 「?」(폰 Figure.UNKNOWN과 같은 모양)와 이름. */
@Composable
private fun HiddenRunner(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = WatchColors.Orange,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)))
                )
            }
            Text(text = "?", style = numberStyle(22.sp, WatchColors.Orange))
        }
        // 「배치고사 상대」가 잘리지 않게 이름 칸을 넓힌다.
        RunnerName(name, maxWidth = 72.dp)
    }
}

@Composable
private fun RunnerName(name: String, maxWidth: Dp = 56.dp) {
    Text(
        text = name,
        modifier = Modifier.widthIn(max = maxWidth),
        style = labelStyle(color = WatchColors.OnSurface, size = 10.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 숨긴 상대 점선 원의 선·틈 길이(px). */
private const val DASH = 8f

/** 얼굴이 옆에서 들어오는 거리, "VS" 처음 크기, 문구가 올라오는 거리. */
private val FACE_SHIFT = 24.dp
private const val VERSUS_START_SCALE = 0.4f
private val MESSAGE_RISE = 8.dp

/** 내 얼굴 원 바탕(피그마 BMB 연보라). */
private val MY_FACE_FILL = Color(0xFFE8E4FF)
