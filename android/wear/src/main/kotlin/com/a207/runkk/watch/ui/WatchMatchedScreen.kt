package com.a207.runkk.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Gender

/**
 * 매칭 완료(FR-086, FR-233, 피그마 BMB): 티어와 거리, 나와 상대 캐릭터 얼굴(성별로 고른다), "VS", "매칭 상대를 찾았어요!".
 * 출발 3초 전에 카운트다운으로 바뀐다(조정자 출발 예약).
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
            MatchHeader(state.tier, state.distance)
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
                Runner(
                    state.opponentName,
                    state.opponentGender,
                    WatchColors.Orange,
                    WatchColors.Orange,
                    Modifier.revealLayer(facesIn, shift = FACE_SHIFT)
                )
            }
            Text(
                text = stringResource(R.string.matched),
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
        Text(
            text = name,
            modifier = Modifier.widthIn(max = 56.dp),
            style = labelStyle(color = WatchColors.OnSurface, size = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 얼굴이 옆에서 들어오는 거리, "VS" 처음 크기, 문구가 올라오는 거리. */
private val FACE_SHIFT = 24.dp
private const val VERSUS_START_SCALE = 0.4f
private val MESSAGE_RISE = 8.dp

/** 내 얼굴 원 바탕(피그마 BMB 연보라). */
private val MY_FACE_FILL = Color(0xFFE8E4FF)
