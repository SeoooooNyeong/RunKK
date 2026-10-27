package com.a207.runkk.app.domain.avatar

/**
 * 러닝 카드 배경(D-185, D-107 (1)): 고른 배경 키와 그 단계(DOMESTIC·EAST_ASIA·INTERCONTINENTAL). 운영 기준값이 더한 키(LM_n)도
 * 단계로 단계 그림을 찾게 한다. 단계를 모르면(목록에 없는 키) 빈 문자열이고 그리는 쪽이 기본 그림을 쓴다.
 */
data class BackgroundChoice(val key: String, val stage: String)
