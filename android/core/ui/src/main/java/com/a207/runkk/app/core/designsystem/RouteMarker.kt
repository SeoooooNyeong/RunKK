package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 경로선 위에 얹는 마커 하나(D-261, D-262): 지점, 이름표(나 "나", 상대 표시명, 셀프런 "10월 2일의 나", 셀럽 이름, 없으면 그리지 않음),
 * 투명도(과거의 나 0.5, D-254), 마커 그림. 그림은 기본이 기쁜 기본 아바타이고 셀럽은 부르는 쪽이 공통 SD 그림을 넘긴다.
 */
@Immutable
data class RouteMarker(
    val point: GeoPoint,
    val label: String? = null,
    val alpha: Float = 1f,
    val tag: String = MY_MARKER_TAG,
    val figure: @Composable () -> Unit = DefaultMarkerFigure,
    /** 이 마커만의 그림 칸 크기. null이면 RouteCanvas의 markerSize다(내 아바타 칸 88dp, D-753). */
    val size: Dp? = null
)

/** 내 마커의 시험 표지. 상대 마커는 [OPPONENT_MARKER_TAG]를 쓴다. */
const val MY_MARKER_TAG = "route-marker-mine"

/** 상대 마커의 시험 표지(ui.battle이 RouteMarker(tag = OPPONENT_MARKER_TAG)로 넘긴다). */
const val OPPONENT_MARKER_TAG = "route-marker-opponent"

/** 기본 마커 그림(기본 아바타 얼굴). 마커마다 새 람다를 만들지 않게 하나를 함께 쓴다. */
private val DefaultMarkerFigure: @Composable () -> Unit = {
    // 지도 위 마커는 얼굴 원이 아니다(목을 늘리지 않는다, D-825).
    RunkkAvatar(
        faceKey = null,
        expression = AvatarExpression.FACE,
        modifier = Modifier.fillMaxSize(),
        isInCircle = false
    )
}
