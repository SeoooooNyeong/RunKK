package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.GeoPoint

/** 지도 위 크루원 한 명(F-15): 내 경로 위 자리, 이름표, 움직이는가. */
internal data class MateMarker(val point: GeoPoint, val label: String, val isMoving: Boolean)
