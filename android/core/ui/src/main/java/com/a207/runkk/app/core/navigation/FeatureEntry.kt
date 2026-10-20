package com.a207.runkk.app.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 기능 등록 계약(5.1.2). 각 ui.* 모듈이 구현을 Hilt @IntoSet으로 등록하고
 * app의 RunkkNavHost가 Set<FeatureEntry>로 그래프를 만든다.
 */
interface FeatureEntry {
    val route: String

    fun register(builder: EntryProviderScope<NavKey>)
}
