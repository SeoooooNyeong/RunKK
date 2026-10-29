package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 칸 이름 → 모든 FeatureEntry가 낸 그리기 함수 목록(D-255, D-298). RunkkNavHost가 FeatureEntry.slots를 모아 넣는다.
 * 넣지 않으면 빈 표다(미리보기·시험).
 */
val LocalSlots = staticCompositionLocalOf<Map<SlotKey, List<@Composable () -> Unit>>> { emptyMap() }

/** 기능 등록 묶음의 칸 기여를 칸 이름별로 모은다. */
fun Collection<FeatureEntry>.collectSlots(): Map<SlotKey, List<@Composable () -> Unit>> =
    flatMap { it.slots.entries }.groupBy({ it.key }, { it.value })
