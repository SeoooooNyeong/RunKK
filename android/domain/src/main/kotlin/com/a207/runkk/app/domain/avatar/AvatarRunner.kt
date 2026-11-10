package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.Distance

/**
 * 내 러너(서버 GET /avatar/runner, 피그마 H-2, contract-W8.md 2.1). 체형 칩·게이지와 렌더러 체형은 [bodyVariant](0 기본, 1 변화 1,
 * 2 변화 2), 렌더러 피부 밝기는 [runAmount](0..100), 피부톤 칩·근거 문구는 [skinTone]·[typicalStartHour](FR-164)를 쓴다.
 *
 * @property bodyShape 서버 체형 4단계(STAGE1~4, FR-163)
 * @property typicalStartHour 피부톤 근거 대표 시각(시 0..23), 없으면 null
 * @property recentRunCount 오늘 포함 최근 7일 유효 러닝 수("이번 주 n번 달렸어요")
 * @property recentDistance 같은 7일 유효 거리
 * @property restDaysBeforeDecline 쉬면 배가 나오기 시작하는 날 수(운영 기준값, 기본 4)
 */
data class AvatarRunner(
    val bodyShape: String,
    val bodyVariant: Int,
    val skinTone: String,
    val typicalStartHour: Int?,
    val recentRunCount: Int,
    val recentDistance: Distance,
    val runAmount: Int,
    val restDaysBeforeDecline: Int
)
