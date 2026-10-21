package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 화면 이동 계약. app의 RunkkNavHost가 백 스택으로 구현해 [LocalRunkkNavigator]로 내려 준다.
 * 화면 모듈은 백 스택을 모르고 경로 이름(Routes)으로만 이동한다(5.1.2, design-review.md M35).
 */
interface RunkkNavigator {
    /** 경로 이름 화면을 위에 올린다. */
    fun navigate(route: String)

    /** 인자 있는 화면(Routes.RunResult 등)을 위에 올린다. */
    fun navigate(key: NavKey)

    /** 한 화면 뒤로. */
    fun back()

    /** 하단 탭으로 옮긴다(홈 위에 탭 화면 하나만 남긴다). */
    fun selectTab(route: String)

    /** 백 스택을 비우고 [route] 하나로 시작한다(로그인 완료·로그아웃·탈퇴, 결과 → 홈). */
    fun restart(route: String)

    /** 미리보기·시험용: 아무것도 하지 않는다. */
    object NoOp : RunkkNavigator {
        override fun navigate(route: String) = Unit

        override fun navigate(key: NavKey) = Unit

        override fun back() = Unit

        override fun selectTab(route: String) = Unit

        override fun restart(route: String) = Unit
    }
}

/** 현재 화면 그래프의 이동기. app이 넣지 않으면 [RunkkNavigator.NoOp]. */
val LocalRunkkNavigator = staticCompositionLocalOf<RunkkNavigator> { RunkkNavigator.NoOp }

/** 인자 없는 경로 하나를 등록한다: FeatureEntry.register 안에서 builder.route(Routes.HOME) { HomeRoute() }. */
fun EntryProviderScope<NavKey>.route(route: String, content: @Composable () -> Unit) {
    addEntryProvider(Routes.Key(route)) { content() }
}
