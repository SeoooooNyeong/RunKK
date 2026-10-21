package com.a207.runkk.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.a207.runkk.app.core.designsystem.RunkkBottomBar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator

/**
 * 화면 그래프를 띄운다(6.1.14). 각 ui.* 모듈이 Hilt @IntoSet으로 등록한 FeatureEntry만 받으므로
 * app은 ui.*를 import하지 않는다(design-review.md M35). 하단 탭(셀프·셀럽·홈·크루·마이)은 맨 위 화면이 탭 경로일 때만 보인다.
 * 화면마다 ViewModel 저장소를 따로 두어(rememberViewModelStoreNavEntryDecorator) 화면을 닫으면 ViewModel도 정리된다.
 */
@Composable
fun RunkkNavHost(
    featureEntries: Set<FeatureEntry>,
    startRoute: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    loggedIn: Boolean = true
) {
    val backStack = rememberNavBackStack(Routes.Key(startRoute))
    val navigator = remember(backStack) { BackStackNavigator(backStack, onExit) }
    // 로그인 상태가 풀리면(로그아웃·토큰 갱신 실패 → TokenProvider.clear, 6.1.14) 로그인·가입 화면이 아닌 한 로그인으로 돌린다.
    LaunchedEffect(loggedIn) {
        val top = (backStack.lastOrNull() as? Routes.Key)?.route
        if (!loggedIn && top !in AUTH_ROUTES) navigator.restart(Routes.LOGIN)
    }
    val provider = remember(featureEntries) {
        entryProvider<NavKey>(fallback = { key -> NavEntry(key) { ComingSoonScreen() } }) {
            featureEntries.forEach { it.register(this) }
        }
    }
    val topRoute = (backStack.lastOrNull() as? Routes.Key)?.route
    val selectedTab = topRoute?.let(::tabOf)
    CompositionLocalProvider(LocalRunkkNavigator provides navigator) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = RunkkColors.Background,
            bottomBar = {
                if (selectedTab != null) {
                    RunkkBottomBar(selected = selectedTab, onSelect = { navigator.selectTab(routeOf(it)) })
                }
            }
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.padding(innerPadding),
                onBack = { navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = provider
            )
        }
    }
}

/** 백 스택 하나로 RunkkNavigator를 구현한다. 마지막 화면에서 뒤로 가면 앱을 닫는다. */
internal class BackStackNavigator(private val backStack: NavBackStack<NavKey>, private val onExit: () -> Unit) :
    RunkkNavigator {
    override fun navigate(route: String) = navigate(Routes.Key(route))

    override fun navigate(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onExit()
    }

    override fun selectTab(route: String) {
        backStack.clear()
        backStack.add(Routes.Key(Routes.HOME))
        if (route != Routes.HOME) backStack.add(Routes.Key(route))
    }

    override fun restart(route: String) {
        backStack.clear()
        backStack.add(Routes.Key(route))
    }
}

/** 로그인하지 않은 채로 머물 수 있는 경로. */
private val AUTH_ROUTES = setOf(Routes.LOGIN, Routes.SIGN_UP)

private fun tabOf(route: String): RunkkTab? = when (route) {
    Routes.SELF -> RunkkTab.SELF
    Routes.CELEB -> RunkkTab.CELEB
    Routes.HOME -> RunkkTab.HOME
    Routes.CREW -> RunkkTab.CREW
    Routes.ME -> RunkkTab.ME
    else -> null
}

private fun routeOf(tab: RunkkTab): String = when (tab) {
    RunkkTab.SELF -> Routes.SELF
    RunkkTab.CELEB -> Routes.CELEB
    RunkkTab.HOME -> Routes.HOME
    RunkkTab.CREW -> Routes.CREW
    RunkkTab.ME -> Routes.ME
}

/** 아직 등록되지 않은 경로(다음 단계 화면). */
@Composable
private fun ComingSoonScreen() {
    val navigator = LocalRunkkNavigator.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RunkkTopBar(title = "", onBack = { navigator.back() })
        RunkkEmptyState(title = "준비 중인 화면이에요", message = "곧 만나요")
    }
}
