package com.a207.runkk.app.ui.record

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.model.GeoPoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

/**
 * Kakao 지도 네이티브 앱 키(빌드 속성 KAKAO_NATIVE_APP_KEY). 기본값은 빈 문자열이라 화면 시험·스크린샷은 늘 Canvas 경로선을 그린다.
 * 실제 화면 경로(RecordDetailRoute)만 BuildConfig 값을 넣는다.
 */
val LocalKakaoMapKey = staticCompositionLocalOf { "" }

/** Kakao 지도 SDK의 네이티브 라이브러리가 있는 ABI(arm). x86 에뮬레이터에서는 지도를 띄우지 않는다. */
private val MAP_ABIS = setOf("arm64-v8a", "armeabi-v7a")

/** 키가 있고 기기가 지도 SDK를 돌릴 수 있으면 참. 아니면 Canvas 경로선을 그린다(C-005 대체). */
internal fun kakaoMapEnabled(key: String, abis: List<String>): Boolean = key.isNotBlank() && abis.any { it in MAP_ABIS }

/**
 * 경로 포인트를 안정·불안정 구간 줄로 나눈다. 이웃한 줄은 끝점을 나눠 가져 선이 끊기지 않는다.
 * 돌려주는 값은 (포인트 번호 구간, 불안정인가).
 */
internal fun routeRuns(pointCount: Int, unstable: List<IntRange>): List<Pair<IntRange, Boolean>> {
    if (pointCount < 2) return emptyList()
    val unstableSegments = unstable.flatMap { range -> (range.first + 1..range.last).toList() }.toSet()
    val runs = mutableListOf<Pair<IntRange, Boolean>>()
    var start = 0
    var current = 1 in unstableSegments
    for (segment in 2 until pointCount) {
        val isUnstable = segment in unstableSegments
        if (isUnstable != current) {
            runs += (start..segment - 1) to current
            start = segment - 1
            current = isUnstable
        }
    }
    runs += (start..pointCount - 1) to current
    return runs
}

/** 지도 SDK를 한 번만 초기화한다. */
private object KakaoMapInit {
    @Volatile
    private var key: String? = null

    @Synchronized
    fun ensure(context: Context, appKey: String) {
        if (key == appKey) return
        KakaoMapSdk.init(context.applicationContext, appKey)
        key = appKey
    }
}

/**
 * Kakao 지도 위 경로선(C-005, 기록 상세). 안정 구간은 라임, 불안정 구간(FR-033)은 라벤더로 그리고 경로 전체가 보이게 맞춘다.
 * 화면 수명에 맞춰 지도를 이어 그리고 멈추며, 화면을 떠나면 지도를 닫는다.
 */
@Composable
internal fun KakaoCourseMap(
    appKey: String,
    points: List<GeoPoint>,
    unstable: List<IntRange>,
    description: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context, appKey) {
        KakaoMapInit.ensure(context, appKey)
        MapView(context)
    }
    DisposableEffect(mapView, points, unstable) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(error: Exception) = Unit
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) = drawRoute(map, points, unstable)
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
            mapView.finish()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier.semantics { contentDescription = description })
}

private fun drawRoute(map: KakaoMap, points: List<GeoPoint>, unstable: List<IntRange>) {
    val latLngs = points.map { LatLng.from(it.latitude, it.longitude) }
    val stable = RouteLineStyles.from(RouteLineStyle.from(LINE_WIDTH, RunkkColors.Lime.toArgb()))
    val shaky = RouteLineStyles.from(RouteLineStyle.from(LINE_WIDTH, RunkkColors.Lavender.toArgb()))
    val segments = routeRuns(points.size, unstable).map { (range, isUnstable) ->
        RouteLineSegment.from(latLngs.subList(range.first, range.last + 1), if (isUnstable) shaky else stable)
    }
    map.routeLineManager?.layer?.addRouteLine(RouteLineOptions.from(segments))
    map.moveCamera(CameraUpdateFactory.fitMapPoints(latLngs.toTypedArray(), FIT_PADDING))
}

/** 이 기기에서 지도를 띄울 수 있는가(키 + arm ABI). */
internal fun canShowKakaoMap(key: String): Boolean = kakaoMapEnabled(key, Build.SUPPORTED_ABIS.toList())

private const val LINE_WIDTH = 12f
private const val FIT_PADDING = 48
