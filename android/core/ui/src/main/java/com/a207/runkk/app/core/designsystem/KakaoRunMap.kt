package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLine as KakaoRouteLine
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

/** 지도 위 다른 마커 하나(대결 상대): 지점, 이름표, 그림. 그림 아래 가운데가 그 지점에 온다. */
@Immutable
data class RunMapMarker(
    val point: GeoPoint,
    val label: String? = null,
    val tag: String = OPPONENT_MARKER_TAG,
    val figure: @Composable () -> Unit
)

/**
 * 측정 중 Kakao 지도(C-005, 사용자 요청 2026-10-30): 지도 위에 경로선을 그리고 카메라가 늘 내 위치(경로 끝)를 가운데에 둔다.
 * 끌기·회전·기울이기는 막고 확대·축소만 받으며, 확대·축소해도 가운데(내 위치)는 그대로다. 새 위치가 와도 사용자가 정한 확대 단계는 지킨다.
 * 내 아바타([figure], 칸 [myMarkerSize], 기본은 [markerSize]. 내 Rive 아바타 칸은 88dp, D-753)와 [others](대결 상대)는 지도 좌표 → 화면 위치(toScreenPoint)로 놓아 확대·축소에도 지도 위 제자리를 지킨다
 * (카메라가 움직이는 동안은 매 프레임 다시 놓는다). 지도를 띄울 수 없으면(키·지도 라이브러리 없음, canShowKakaoMap) 부르는 쪽이 RouteCanvas를 그린다.
 * 실행 중 지도 오류(잘못된 키·네트워크, MapLifeCycleCallback.onMapError)면 [onMapError]를 알리고, 부르는 쪽이 그 상태를
 * canShowKakaoMap(key, isMapFailed)에 넘겨 격자 바탕 RouteCanvas로 바꾼다(FR-034, D-322 (3), D-340).
 * 기록 상세 KakaoCourseMap과 같이 화면 수명에 맞춰 지도를 이어 그리고 멈추며, 화면을 떠나면 닫는다.
 * [isFramed]가 거짓이면 둥근 모서리·테두리 없이 칸 그대로 자른다(크루런 F-15는 가장자리 페이드를 부르는 쪽이 덮는다).
 */
@Composable
fun KakaoRunMap(
    route: RouteLine,
    modifier: Modifier = Modifier,
    markerSize: Dp = 72.dp,
    others: List<RunMapMarker> = emptyList(),
    myMarkerSize: Dp = markerSize,
    myLabel: String? = null,
    onMapError: () -> Unit = {},
    isFramed: Boolean = true,
    figure: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appKey = LocalKakaoMapKey.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lineColor = RunkkColors.Lime.toArgb()
    val mapView = remember(context, appKey) {
        ensureKakaoMap(context, appKey)
        MapView(context)
    }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var drawn by remember { mutableStateOf<KakaoRouteLine?>(null) }
    var placed by remember { mutableStateOf<List<IntOffset?>>(emptyList()) }
    var placedMe by remember { mutableStateOf<IntOffset?>(null) }
    var isCameraMoving by remember { mutableStateOf(false) }
    var isZoomSet by remember { mutableStateOf(false) }
    val latestOthers by rememberUpdatedState(others)
    val latestRoute by rememberUpdatedState(route)
    val latestOnMapError by rememberUpdatedState(onMapError)
    fun place(ready: KakaoMap) {
        fun screen(point: GeoPoint): IntOffset? =
            ready.toScreenPoint(LatLng.from(point.latitude, point.longitude))?.let { IntOffset(it.x, it.y) }
        placed = latestOthers.map { screen(it.point) }
        placedMe = latestRoute.points.lastOrNull()?.let(::screen)
    }
    DisposableEffect(mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                // 지도 SDK가 다른 스레드에서 부를 수 있어 화면 스레드로 넘긴다
                override fun onMapError(error: Exception) {
                    mapView.post { latestOnMapError() }
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(ready: KakaoMap) {
                    // 지도는 나를 따라다니고 확대·축소만 된다(끌기·회전·기울이기 막음, 확대·축소 중에도 가운데 고정)
                    listOf(
                        GestureType.Pan,
                        GestureType.Rotate,
                        GestureType.Tilt,
                        GestureType.LongTapAndDrag,
                        GestureType.RotateZoom
                    )
                        .forEach { ready.setGestureEnable(it, false) }
                    ready.setFixedCenter(
                        true,
                        GestureType.Zoom,
                        GestureType.OneFingerZoom,
                        GestureType.OneFingerDoubleTap,
                        GestureType.TwoFingerSingleTap
                    )
                    ready.setOnCameraMoveStartListener { _, _ -> isCameraMoving = true }
                    ready.setOnCameraMoveEndListener { moved, _, _ ->
                        isCameraMoving = false
                        place(moved)
                    }
                    map = ready
                }
            }
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map = null
            mapView.finish()
        }
    }
    // 새 포인트가 오면 경로선을 다시 그리고 카메라를 내 위치로 옮긴다. 상대 자리도 다시 놓는다.
    // 상대 그림 람다는 다시 그릴 때마다 새로 생기므로 좌표만 기준으로 다시 그린다
    val otherPoints = others.map { it.point }
    LaunchedEffect(map, route, otherPoints) {
        val ready = map ?: return@LaunchedEffect
        val layer = ready.routeLineManager?.layer ?: return@LaunchedEffect
        val latLngs = route.points.map { LatLng.from(it.latitude, it.longitude) }
        // 새 선을 먼저 그린 뒤 옛 선을 지운다(먼저 지우면 그 사이 선이 잠깐 사라진다)
        val previous = drawn
        drawn = if (latLngs.size >= 2) {
            val style = RouteLineStyles.from(RouteLineStyle.from(LINE_WIDTH, lineColor))
            layer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(latLngs, style)))
        } else {
            null
        }
        previous?.let { layer.remove(it) }
        // 처음 한 번만 따라가기 확대 단계로 맞추고, 그 뒤에는 사용자가 정한 확대 단계를 두고 가운데만 옮긴다
        latLngs.lastOrNull()?.let { center ->
            val update = if (isZoomSet) {
                CameraUpdateFactory.newCenterPosition(center)
            } else {
                CameraUpdateFactory.newCenterPosition(center, FOLLOW_ZOOM)
            }
            ready.moveCamera(update)
            isZoomSet = true
        }
        place(ready)
    }
    // 지도를 끄는 동안 아바타가 지도 위 제자리를 따라가게 매 프레임 다시 놓는다
    LaunchedEffect(map, isCameraMoving) {
        val ready = map ?: return@LaunchedEffect
        while (isCameraMoving) {
            withFrameNanos { }
            place(ready)
        }
    }
    Box(
        modifier = if (isFramed) {
            modifier.clip(MAP_SHAPE).border(1.dp, RunkkColors.Border, MAP_SHAPE)
        } else {
            modifier.clipToBounds()
        },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // 상대는 내 아바타보다 먼저(아래에) 그린다
        others.forEachIndexed { i, other ->
            val at = placed.getOrNull(i) ?: return@forEachIndexed
            MapFigure(other.label, markerSize, Modifier.testTag(other.tag).atScreenPoint(at, markerSize)) {
                other.figure()
            }
        }
        // 내 위치를 아직 못 놓았으면(지도 준비 전) 가운데에 둔다: 카메라가 내 위치를 가운데에 두므로 발이 가운데에 오게 반 칸 올린다
        val me = placedMe
        val mine = if (me != null) Modifier.atScreenPoint(me, myMarkerSize) else Modifier.offset(y = -myMarkerSize / 2)
        MapFigure(myLabel, myMarkerSize, Modifier.testTag(MY_MARKER_TAG).then(mine)) { figure() }
    }
}

/** 지도 위 그림 하나와 아래 이름표(RouteCanvas 마커와 같은 모양). */
@Composable
private fun MapFigure(label: String?, size: Dp, modifier: Modifier, figure: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(Modifier.size(size)) { figure() }
        label?.let {
            Text(
                it,
                style = RunkkTextStyles.text(11, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier
                    .background(RunkkColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 지도 화면 좌표 [at](지도 왼쪽 위 기준)에 그림 아래 가운데가 오게 놓는다. 부모가 가운데 정렬로 놓으므로 그만큼을 빼서 옮긴다.
 */
private fun Modifier.atScreenPoint(at: IntOffset, size: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place(
            at.x - constraints.maxWidth / 2,
            at.y - size.roundToPx() - (constraints.maxHeight - placeable.height) / 2
        )
    }
}

private val MAP_SHAPE = RoundedCornerShape(20.dp)
private const val LINE_WIDTH = 10f

/** 따라가기 확대 단계(골목 몇 개가 보이는 정도). */
private const val FOLLOW_ZOOM = 17
