package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 기능 등록 계약(5.1.2). 각 ui.* 모듈이 구현을 Hilt @IntoSet으로 등록하고
 * app의 RunkkNavHost가 Set<FeatureEntry>로 그래프를 만든다.
 */
interface FeatureEntry {
    val route: String

    fun register(builder: EntryProviderScope<NavKey>)

    /**
     * 화면 그래프 위에 늘 붙는 기능 화면(예: 앱을 다시 켰을 때 남은 대결 안내). 로그인한 동안 RunkkNavHost가 그린다.
     * 대부분의 기능은 없으므로 기본은 아무것도 그리지 않는다.
     */
    @Composable
    fun Overlay() = Unit
}
