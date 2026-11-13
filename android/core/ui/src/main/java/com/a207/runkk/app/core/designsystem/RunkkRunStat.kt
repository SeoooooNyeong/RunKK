package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * 러닝 지표 한 칸([RunkkRunStats]): 값 [value]("27:48"), 라벨 [label]("시간"), 보조 기술이 읽을 값 [spoken]
 * ("27분 48초", 없으면 값과 라벨을 그대로 읽는다).
 */
@Immutable
data class RunkkRunStat(val value: String, val label: String, val spoken: String? = null)
