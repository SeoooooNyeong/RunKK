package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.RouteLine

/**
 * 나만 그리는 지도([임시:D-성-263], [임시:FR-성-28], [임시:FR-성-31]): 내가 달린 길 [route]를 실선으로, 지금 위치에 [figure](내 얼굴 원,
 * 칸 [myMarkerSize])와 이름표 [myLabel](「나」)을 그린다. 상대 마커, 남은 길, 도착점은 그리지 않는다. 오른쪽 아래 「내 위치로」 단추는
 * 사용자가 바꾼 확대 단계를 버리고 내 위치를 가운데로 되돌린다. 지도를 띄울 수 있는지(canShowKakaoMap)는 부르는 쪽이 보고, 실행 중 지도
 * 오류는 [onMapError]로 알린다(부르는 쪽이 트랙 보기나 격자로 바꾼다).
 */
@Composable
fun RunkkMeMap(
    route: RouteLine,
    myMarkerSize: Dp,
    onMapError: () -> Unit,
    modifier: Modifier = Modifier,
    myLabel: String? = null,
    figure: @Composable () -> Unit
) {
    var recenterRequest by remember { mutableIntStateOf(0) }
    Box(modifier.clip(RunkkShapes.ExtraLarge)) {
        KakaoRunMap(
            route = route,
            modifier = Modifier.fillMaxSize(),
            others = emptyList(),
            myMarkerSize = myMarkerSize,
            myLabel = myLabel,
            onMapError = onMapError,
            isFramed = false,
            recenterRequest = recenterRequest,
            figure = figure
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .heightIn(min = 48.dp)
                .testTag(MAP_RECENTER_TAG)
                .clickable(role = Role.Button) { recenterRequest += 1 }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RunkkShapes.Pill)
                    .background(RunkkColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    RunkkIcons.MyLocation,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    stringResource(R.string.map_recenter),
                    style = RunkkTextStyles.text(12, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

/** 「내 위치로」 단추의 시험 표지. */
const val MAP_RECENTER_TAG = "map-recenter"
