package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.time.Instant

/**
 * 러닝 결과(B-6, D-282, 상용안 FR-289) 화면 값. 티어 표시는 없다. 가장 빠른 1km·소모 열량·날씨와 유효·집계 제외 표시는 결과 화면에 없다(기록 상세에서 본다, D-26·D29).
 */
data class RunResultUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val cardKind: CardKind? = null,
    val startedAt: Instant? = null,
    val distance: Distance? = null,
    val elapsed: Duration? = null,
    val averagePace: Pace? = null,
    val averageHeartRate: Int? = null,
    /** 1km 구간 페이스(서버 splitPaces). 서버에 올라가기 전에는 비어 있다. */
    val splitPaces: List<Pace> = emptyList(),
    val newRecords: List<String> = emptyList(),
    /** 서버 심박 구간(Z1~Z5 → 시간). 출생연도가 없거나 심박이 없으면 비어 있다. 화면은 4단계로 합쳐 그린다(J24-17). */
    val heartRateZones: Map<String, Duration> = emptyMap(),
    /** 이 러닝의 리워드 적립 합계(서버 rewardPoints, "리워드 +n"). */
    val rewardPoints: Int? = null,
    /** 정지 구간 합계("휴식 n분 n초", FR-194). 기기 보관 기록의 정지 구간이나 서버 pausedSeconds. 없거나 0이면 null. */
    val pausedTotal: Duration? = null,
    /** 서버에 저장된 기록(구간 페이스·신기록·리워드가 붙음)인가. 거짓이면 기기 보관 기록이고 전송 대기 중이다. */
    val isUploaded: Boolean = false,
    val error: UiText? = null,
    /** 출생연도가 있어 심박 구간을 낼 수 있는가. 심박은 있는데 구간이 없으면 거짓(「출생연도를 입력하면…」). */
    val hasBirthYear: Boolean = true,
    /** 심박 출처(「워치」 칩). 이 앱의 심박은 워치에서만 온다. */
    val heartRateSource: String? = null,
    /** 이번 러닝 성과 줄(신기록, 리워드). 결과 화면은 이것을 그린다. */
    val achievements: List<AchievementRow> = emptyList()
) {
    companion object {
        /** 워치 심박 출처 이름. */
        const val HEART_SOURCE_WATCH = "WATCH"
    }
}
