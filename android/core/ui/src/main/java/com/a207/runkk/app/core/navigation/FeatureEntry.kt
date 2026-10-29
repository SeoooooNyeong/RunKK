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
     * 다른 화면 모듈이 그리는 칸에 이 기능이 넣는 그리기 함수(D-255, D-298). RunkkNavHost가 모든 기능의 표를 모아
     * [LocalSlots]로 내려 주고, 칸을 가진 화면은 `LocalSlots.current[SlotKey.X]`만 그린다. 대부분의 기능은 넣지 않는다.
     */
    val slots: Map<SlotKey, @Composable () -> Unit> get() = emptyMap()

    /**
     * 화면 그래프 위에 늘 붙는 기능 화면(예: 앱을 다시 켰을 때 남은 대결 안내, 홈 위 첫 사용 가이드). 로그인한 동안 RunkkNavHost가
     * 그린다. [top]은 백 스택 맨 위 경로이고, 쓰지 않는 기능은 무시한다(D-202, D-255). 대부분의 기능은 없으므로 기본은 아무것도 그리지 않는다.
     */
    @Composable
    fun Overlay(top: NavKey?) = Unit
}
