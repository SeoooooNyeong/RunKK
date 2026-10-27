package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.time.Instant

data class RunResultUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val cardKind: CardKind? = null,
    val startedAt: Instant? = null,
    val distance: Distance? = null,
    val elapsed: Duration? = null,
    val averagePace: Pace? = null,
    val averageHeartRate: Int? = null,
    val route: List<GeoPoint> = emptyList(),
    val weather: String? = null,
    val calories: Int? = null,
    val newRecords: List<String> = emptyList(),
    val heartRateZones: List<Pair<String, Duration>> = emptyList(),
    /** 정지 구간 합계(기기 보관 기록의 정지 구간, FR-194). 모르거나 정지가 없으면 null. */
    val pausedTotal: Duration? = null,
    /** 서버에 저장된 기록(날씨·칼로리·신기록이 붙음)인가. 거짓이면 기기 보관 기록이고 전송 대기 중이다. */
    val isUploaded: Boolean = false,
    /**
     * 서버가 무효로 판정해 집계에서 뺀 기록인가(FR-047). 서버 기록을 읽은 뒤에만 참이 될 수 있다: 미전송(판정 전)이면 거짓이다.
     * 참이면 "집계 제외"를 보이고 칼로리는 보이지 않는다.
     */
    val isExcluded: Boolean = false,
    val error: UiText? = null
)
