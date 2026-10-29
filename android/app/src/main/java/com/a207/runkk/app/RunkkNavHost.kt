package com.a207.runkk.app

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.a207.runkk.app.core.designsystem.GuideAnchors
import com.a207.runkk.app.core.designsystem.LocalGuideAnchors
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkBottomBar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.core.navigation.collectSlots

/**
 * 화면 그래프를 띄운다(6.1.14). 각 ui.* 모듈이 Hilt @IntoSet으로 등록한 FeatureEntry만 받으므로
 * app은 ui.*를 import하지 않는다(design-review.md M35). 하단 탭(크루·기록런·홈·랭킹·마이, D-250)은 맨 위 화면이 탭 경로이거나
 * 배틀런 화면(홈을 고른 상태)일 때만 보인다.
 * 화면마다 ViewModel 저장소를 따로 두어(rememberViewModelStoreNavEntryDecorator) 화면을 닫으면 ViewModel도 정리된다.
 * [lockUnreleased]가 켜지면(1차 배포 빌드, D-251) 크루 탭에 자물쇠를 달고 누르면 [LockedFeatureDialog]만 띄우며(D-253),
 * 잠긴 경로([BetaLock])로 들어오면 안내를 띄우고 뒤로 돌린다. 화면 숨김 판정(LocalReleaseScope), 가이드 앵커 표
 * (LocalGuideAnchors), 칸 기여 표(LocalSlots)도 여기서 넣는다. 초대 링크로 받은 [pendingInvite]는 홈이 맨 위가 되면 참가 확인으로 연다.
 */
@Composable
fun RunkkNavHost(
    featureEntries: Set<FeatureEntry>,
    startRoute: String,
    onExit: () -> Unit,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
    lockUnreleased: Boolean = BuildConfig.LOCK_UNRELEASED,
    pendingInvite: String? = null,
    onInviteOpened: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(Routes.Key(startRoute))
    val navigator = remember(backStack) { BackStackNavigator(backStack, onExit) }
    // 초대 링크(D-272): 로그인 상태이고 홈이 맨 위일 때 참가 확인을 연다. 로그인·가입 중이거나 측정 중이면 홈이 맨 위가 될 때까지 기다린다.
    val current = backStack.lastOrNull()
    LaunchedEffect(pendingInvite, loggedIn, current) {
        inviteToOpen(pendingInvite, loggedIn, current)?.let { token ->
            navigator.navigate(Routes.FriendInviteJoin(token))
            onInviteOpened()
        }
    }
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
    val top = backStack.lastOrNull()
    val selectedTab = (top as? Routes.Key)?.route?.let(::tabOf)
    val lockedTabs = if (lockUnreleased) LOCKED_TABS else emptySet()
    var isLockedNoticeVisible by rememberSaveable { mutableStateOf(false) }
    val guideAnchors = remember { GuideAnchors() }
    val slots = remember(featureEntries) { featureEntries.collectSlots() }
    CompositionLocalProvider(
        LocalRunkkNavigator provides navigator,
        LocalReleaseScope provides ReleaseScope.of(lockUnreleased),
        LocalGuideAnchors provides guideAnchors,
        LocalSlots provides slots
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = RunkkColors.Background,
            bottomBar = {
                if (selectedTab != null) {
                    RunkkBottomBar(
                        selected = selectedTab,
                        onSelect = { tab ->
                            // 잠긴 탭은 이동하지 않고 안내만 띄운다(서버 요청 없음, D-253).
                            if (tab in lockedTabs) isLockedNoticeVisible = true else navigator.selectTab(routeOf(tab))
                        },
                        lockedTabs = lockedTabs
                    )
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
        // 기능 화면 위 안내(남은 대결, 홈 위 첫 사용 가이드 등)는 로그인한 동안만 붙인다. 맨 위 경로를 넘긴다(D-255).
        if (loggedIn) featureEntries.forEach { it.Overlay(top) }
        if (isLockedNoticeVisible) LockedFeatureDialog(onDismiss = { isLockedNoticeVisible = false })
    }
}

/** 지금 열 초대 토큰: 받은 토큰이 있고 로그인 상태이며 홈이 맨 위일 때만(D-272). 그 밖에는 null(기다린다). */
internal fun inviteToOpen(pendingInvite: String?, loggedIn: Boolean, top: NavKey?): String? =
    pendingInvite?.takeIf { loggedIn && top == Routes.Key(Routes.HOME) }

/** 1차 배포에서 잠그는 하단 탭(D-253). */
private val LOCKED_TABS: Set<RunkkTab> = setOf(RunkkTab.CREW)

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

    override fun replace(key: NavKey) {
        if (backStack.isEmpty()) backStack.add(key) else backStack[backStack.lastIndex] = key
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

/** 경로 → 하단 탭(D-250). 배틀런 화면은 탭이 아니지만 홈을 고른 상태로 하단 탭을 보인다. */
internal fun tabOf(route: String): RunkkTab? = when (route) {
    Routes.CREW -> RunkkTab.CREW
    Routes.RECORD_RUN -> RunkkTab.RECORD_RUN
    Routes.HOME, Routes.BATTLE_MODE -> RunkkTab.HOME
    Routes.RANKING -> RunkkTab.RANKING
    Routes.ME -> RunkkTab.ME
    else -> null
}

internal fun routeOf(tab: RunkkTab): String = when (tab) {
    RunkkTab.CREW -> Routes.CREW
    RunkkTab.RECORD_RUN -> Routes.RECORD_RUN
    RunkkTab.HOME -> Routes.HOME
    RunkkTab.RANKING -> Routes.RANKING
    RunkkTab.ME -> Routes.ME
}

/** 1차 배포에서 잠근 경로([BetaLock])는 등록된 화면 대신 잠긴 기능 안내를 띄우고 뒤로 돌린다(우회 진입 방어, D-251). */
private fun withBetaLock(entries: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    if (BetaLock.isLocked(key)) NavEntry(key) { LockedRouteScreen() } else entries(key)
}

/** 잠긴 경로 자리: 빈 바탕 위 잠긴 기능 안내. 닫으면 한 화면 뒤로. */
@Composable
private fun LockedRouteScreen() {
    val navigator = LocalRunkkNavigator.current
    Box(Modifier.fillMaxSize().background(RunkkColors.Background))
    LockedFeatureDialog(onDismiss = { navigator.back() })
}

/** 아직 등록되지 않은 경로(다음 단계 화면). */
@Composable
private fun ComingSoonScreen() {
    val navigator = LocalRunkkNavigator.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RunkkTopBar(title = "", onBack = { navigator.back() })
        RunkkEmptyState(
            title = stringResource(R.string.app_coming_soon_title),
            message = stringResource(R.string.app_coming_soon_message)
        )
    }
}
