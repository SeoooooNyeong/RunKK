package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import kotlin.math.abs
import kotlin.math.min

/**
 * 대결 상대 마커 위치(D-262). 상대 마커는 내 경로 위 "내 거리 + 격차" 지점이다: 상대가 뒤(격차 0 이하)면 경로를 거슬러 간 지점,
 * 앞(격차 양수)이면 경로 끝에서 진행 방향으로 연장한 지점이다. 진행값 메시지에 상대 좌표를 싣지 않는다(FR-089).
 * 내 거리는 경로 길이보다 클 수 있어(초과 시속 구간·불안정 구간) 둘 중 작은 값을 "내 위치"로 본다. 내 마커는 경로 끝에 그린다.
 */
object OpponentMarkerPlacer {
    /**
     * 상대 마커 지점. 격차를 모르거나(null, 연결 확인 중) 경로가 [RouteLine.DIRECTION_METERS] 미만이면 null(그리지 않는다).
     * 뒤선 상대가 출발점보다 뒤면 그릴 곳이 없어 null이다.
     */
    fun place(route: RouteLine, mine: Distance, gapMeters: Int?): GeoPoint? {
        val gap = gapMeters ?: return null
        if (!route.isLongEnoughForDirection) return null
        if (gap > 0) return route.extendedBy(gap)
        val here = min(mine.meters.toDouble(), route.lengthMeters)
        return route.pointAtMeters(here - abs(gap))
    }
}
