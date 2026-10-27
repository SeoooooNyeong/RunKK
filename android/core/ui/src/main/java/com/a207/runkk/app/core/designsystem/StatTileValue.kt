package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * 통계 칸의 값: 큰 숫자 [text], 숫자 뒤 작은 글자 [unit]("KM", "%"), 보조 기술이 읽을 문구 [spoken].
 * spoken이 있으면 "평균 페이스 1킬로미터당 5분 32초"처럼 읽는다("5:32"가 시각으로 읽히지 않게).
 */
@Immutable
data class StatTileValue(val text: String, val unit: String? = null, val spoken: String? = null)
