package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 공유 카드 값(서버 running.RunCardData, D-22). 앞면은 기록, 뒷면은 지도 + 경로선(route).
 * courseMapShared가 거짓이면 뒷면 공유를 거부한다(FR-069).
 * 앞면(B-7, FR-068)은 날짜·시각, 날씨([weather], 서버 temperatureC·sky, D-235), 거리, 신기록([newRecords]가 비어 있지 않으면),
 * 운동 시간·평균 페이스·평균 심박, "[닉네임], 런크크" 서명([ownerName])을 쓴다. 새 값은 옛 서버면 비어 있다(D-289).
 */
data class RunCard(
    val recordId: String,
    val cardKind: CardKind,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val route: List<GeoPoint>,
    val isCourseMapShared: Boolean,
    val averageHeartRate: Int? = null,
    val newRecords: List<String> = emptyList(),
    val weather: CardWeather? = null,
    val ownerName: String? = null
)

/** 카드 앞면 날씨(D-235): 기온(°C)과 하늘 상태. 하늘 상태는 서버 SkyCondition 이름이고 모르는 값이면 null이다. */
data class CardWeather(val temperatureC: Int?, val sky: Sky?) {
    /** 서버 running.SkyCondition과 같은 이름. */
    enum class Sky {
        CLEAR,
        CLOUDY,
        OVERCAST,
        RAIN,
        SNOW
    }
}
