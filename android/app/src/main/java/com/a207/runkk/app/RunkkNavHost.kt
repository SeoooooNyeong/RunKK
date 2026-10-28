package com.a207.runkk.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
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
 * [lockUnreleased]가 켜지면(베타 prod 빌드) 아직 열지 않는 기능([BetaLock])은 "추후 추가 예정" 화면으로 바꾼다.
 */
@Composable
fun RunkkNavHost(
    featureEntries: Set<FeatureEntry>,
    startRoute: String,
    onExit: () -> Unit,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
    lockUnreleased: Boolean = BuildConfig.LOCK_UNRELEASED
) {
    val backStack = rememberNavBackStack(Routes.Key(startRoute))
    val navigator = remember(backStack) { BackStackNavigator(backStack, onExit) }
    // 로그인 상태가 풀리면(로그아웃·토큰 갱신 실패 → TokenProvider.clear, 6.1.14) 로그인·가입 화면이 아닌 한 로그인으로 돌린다.
    LaunchedEffect(loggedIn) {
        val top = (backStack.lastOrNull() as? Routes.Key)?.route
        if (!loggedIn && top !in AUTH_ROUTES) navigator.restart(Routes.LOGIN)
    }
    val provider = remember(featureEntries, lockUnreleased) {
        val entries = entryProvider<NavKey>(fallback = { key -> NavEntry(key) { ComingSoonScreen() } }) {
            featureEntries.forEach { it.register(this) }
        }
        withBottomSpace(if (lockUnreleased) withBetaLock(entries) else entries)
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
            // 위·옆 여백만 여기서 띄운다. 아래(내비게이션 바·하단 탭)는 화면마다 EntryBottomSpace가 띄운다.
            val direction = LocalLayoutDirection.current
            val topAndSides = PaddingValues(
                start = innerPadding.calculateStartPadding(direction),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(direction)
            )
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.padding(topAndSides).consumeWindowInsets(topAndSides),
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

/**
 * 화면마다 아래 여백(하단 탭 자리)을 그 화면의 경로로 정한다. 맨 위 경로로 정하면 탭 화면에서 탭이 아닌 화면으로 갈 때 하단 탭이 먼저
 * 사라져, 전환(페이드 700ms)이 끝날 때까지 남아 있는 나가는 화면이 탭 높이만큼 늘어난다(홈 우리 집 아바타 → 아바타 꾸미기).
 */
private fun withBottomSpace(entries: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = entries(key)
    val isTab = (key as? Routes.Key)?.route?.let(::tabOf) != null
    NavEntry(navEntry = entry) { EntryBottomSpace(isTab) { entry.Content() } }
}

/**
 * 화면 하나의 아래 여백: 내비게이션 바, 탭 화면이면 하단 탭 높이([RUNKK_BOTTOM_BAR_HEIGHT])를 더한다. 맨 위 경로가 바뀌어도 이 화면의
 * 여백은 그대로라 전환 중 크기가 바뀌지 않는다. 띄운 만큼은 consumeWindowInsets로 빼고, 키보드(IME)가 그보다 높으면 그만큼 올린다.
 */
@Composable
private fun EntryBottomSpace(isTab: Boolean, content: @Composable () -> Unit) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = PaddingValues(bottom = navigationBar + if (isTab) RUNKK_BOTTOM_BAR_HEIGHT else 0.dp)
    Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom).imePadding()) { content() }
}

/** 로그인하지 않은 채로 머물 수 있는 경로. */
private val AUTH_ROUTES = setOf(Routes.LOGIN, Routes.SIGN_UP, Routes.GUEST_START)

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

/** 베타에서 열지 않는 기능([BetaLock])은 등록된 화면 대신 "추후 추가 예정" 화면을 띄운다. */
private fun withBetaLock(entries: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    if (BetaLock.isLocked(key)) {
        NavEntry(key) {
            ComingSoonScreen(R.string.app_beta_locked_title, R.string.app_beta_locked_message)
        }
    } else {
        entries(key)
    }
}

/** 아직 등록되지 않은 경로(다음 단계 화면), 베타에서 열지 않는 기능. */
@Composable
private fun ComingSoonScreen(
    @StringRes title: Int = R.string.app_coming_soon_title,
    @StringRes message: Int = R.string.app_coming_soon_message
) {
    val navigator = LocalRunkkNavigator.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RunkkTopBar(title = "", onBack = { navigator.back() })
        RunkkEmptyState(title = stringResource(title), message = stringResource(message))
    }
}
