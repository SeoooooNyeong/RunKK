package com.a207.runkk.app

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 경로마다 이름만 보이는 가짜 화면들. 홈에는 크루 경로로 가는 버튼(알림·딥링크 우회 진입 대신)과 크루 공개 여부를 둔다. */
private object LockEntries : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) {
            val navigator = LocalRunkkNavigator.current
            val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
            Text("홈 화면 크루 공개 $isCrewOpen")
            TextButton(onClick = { navigator.navigate(Routes.CrewHome(1)) }) { Text("크루 알림 열기") }
        }
        builder.route(Routes.CREW) { Text("크루 화면") }
        builder.route(Routes.RECORD_RUN) { Text("기록런 화면") }
        builder.route(Routes.RANKING) { Text("랭킹 화면") }
        builder.route(Routes.ME) { Text("마이 화면") }
        builder.addEntryProvider(Routes.CrewHome::class) { Text("크루 홈 화면") }
    }
}

/**
 * 미공개 기능 경로 잠금(D-251~D-253, FR-223): 경로마다 딸린 기능(크루·아바타 꾸미기·런바디)이 지금 범위(ReleaseScope)에서 닫혀 있을 때만
 * 막는다. 2차 배포(운영 빌드, S15P31A207-248)는 셋 다 열려 있어 막는 경로가 없고, 1차 배포 범위로 돌리면 예전처럼 막는다.
 * 시험 설계 10-06 TC-9691~TC-9697, 10-07 TC-9859(가짜 서버 없이 화면 그래프만, 등록된 가짜 화면은 서버를 부르지 않는다).
 */
@RunWith(RobolectricTestRunner::class)
class BetaLockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-9691 크루 경로는 크루, 아바타 경로는 아바타 꾸미기, 런바디 경로는 런바디에 딸린다`() {
        listOf<NavKey>(
            Routes.Key(Routes.CREW),
            Routes.Key(Routes.CREW_CREATE),
            Routes.Key(Routes.CREW_CHAT),
            Routes.Key(Routes.CREW_NOTICE),
            Routes.CrewHome(1),
            Routes.CrewChat(1),
            Routes.CrewRunLobby(1)
        ).forEach { assertEquals(UnreleasedFeature.CREW, BetaLock.featureOf(it), "$it") }
        listOf(Routes.AVATAR, Routes.AVATAR_SETUP, Routes.SHOP, Routes.AVATAR_BACKGROUND).forEach {
            assertEquals(UnreleasedFeature.AVATAR_CUSTOMIZE, BetaLock.featureOf(Routes.Key(it)), it)
        }
        assertEquals(UnreleasedFeature.RUNBODY, BetaLock.featureOf(Routes.Key(Routes.REPORT)))
        assertEquals(UnreleasedFeature.RUNBODY, BetaLock.featureOf(Routes.Key(Routes.COACH)))
        assertEquals(UnreleasedFeature.RUNBODY, BetaLock.featureOf(Routes.Key(Routes.COACH_CONSENTS)))
    }

    @Test
    fun `S15P31A207-248 운영 빌드 범위는 크루·아바타·런바디 경로를 막지 않고 1차 배포 범위는 막는다`() {
        val prod = ReleaseScope.of(isLockingUnreleased = true)
        listOf<NavKey>(
            Routes.Key(Routes.CREW),
            Routes.CrewHome(1),
            Routes.Key(Routes.SHOP),
            Routes.Key(Routes.AVATAR),
            Routes.Key(Routes.REPORT)
        ).forEach {
            assertFalse(BetaLock.isLocked(it, prod), "운영 빌드에서 열린다: $it")
            assertTrue(BetaLock.isLocked(it, ReleaseScope.FIRST_RELEASE), "1차 배포 범위에서 닫힌다: $it")
        }
        assertEquals(emptySet(), BetaLock.lockedTabs(prod))
        assertEquals(setOf(RunkkTab.CREW), BetaLock.lockedTabs(ReleaseScope.FIRST_RELEASE))
    }

    @Test
    fun `하단 탭 경로는 크루·기록런·홈·랭킹·마이 순서이고 친구 대결 화면은 홈 탭을 고른다`() {
        assertEquals(listOf(Routes.CREW, Routes.RECORD_RUN, Routes.HOME, Routes.RANKING, Routes.ME), Routes.TABS)
        Routes.TABS.forEach { assertEquals(it, tabOf(it)?.let(::routeOf)) }
        assertEquals(Routes.HOME, tabOf(Routes.BATTLE_FRIEND)?.let(::routeOf))
        assertEquals(null, tabOf("battle/self"))
        assertEquals("ranking", Routes.RANKING)
    }

    @Test
    fun `TC-5909 배틀런 두 분절 화면 경로는 없다`() {
        val constants = Routes::class.java.declaredFields.map { it.name }
        assertFalse("BATTLE_MODE" in constants, "배틀런 두 분절 화면(BATTLE_MODE)은 랜덤·친구 대결로 나눴다(CR-142)")
        assertEquals(null, tabOf("battle/mode"))
    }

    @Test
    fun `TC-5830 배치고사·랜덤 대결은 하단 내비를 숨기고 친구 대결은 홈 탭이다`() {
        assertEquals(RunkkTab.HOME, tabOf(Routes.BATTLE_FRIEND))
        assertEquals(RunkkTab.HOME, tabOf(Routes.Key(Routes.BATTLE_FRIEND)))
        listOf<NavKey>(
            Routes.Key(Routes.BATTLE_RANDOM),
            Routes.Key(Routes.PLACEMENT),
            Routes.Key(Routes.PLACEMENT_COMPLETE),
            Routes.PlacementResult(1)
        ).forEach { key ->
            assertEquals(null, tabOf(key), "$key")
            assertFalse(BetaLock.isLocked(key, ReleaseScope.FIRST_RELEASE), "1차 배포에서 열려 있다: $key")
        }
    }

    @Test
    fun `TC-9693 S15P31A207-248 운영 빌드는 크루 알림 경로로 바로 들어와도 안내 없이 연다`() {
        show(ReleaseScope.of(isLockingUnreleased = true))

        compose.onNodeWithText("홈 화면 크루 공개 true").assertExists()
        compose.onNodeWithText("크루 알림 열기").performClick()
        compose.onNodeWithText("크루 홈 화면").assertExists()
        compose.onNodeWithText("추후 추가 예정").assertDoesNotExist()
    }

    @Test
    fun `S15P31A207-248 운영 빌드의 크루 탭은 자물쇠 없이 크루 화면으로 간다`() {
        show(ReleaseScope.of(isLockingUnreleased = true))

        compose.onNodeWithText("크루").performClick()
        compose.onNodeWithText("크루 화면").assertExists()
        compose.onNodeWithText("추후 추가 예정").assertDoesNotExist()
    }

    @Test
    fun `TC-9695 TC-9859 크루가 닫힌 범위의 크루 탭은 이동 없이 추후 추가 예정만 보이고 홈·기록런 탭은 이동한다`() {
        show(ReleaseScope.FIRST_RELEASE)

        compose.onNodeWithText("크루").performClick()
        compose.onNodeWithText("추후 추가 예정").assertExists()
        compose.onNodeWithText("크루 화면").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("추후 추가 예정").assertDoesNotExist()

        compose.onNodeWithText("기록런").performClick()
        compose.onNodeWithText("기록런 화면").assertExists()
        compose.onNodeWithText("홈").performClick()
        compose.onNodeWithText("홈 화면 크루 공개 false").assertExists()
    }

    @Test
    fun `TC-9696 TC-9859 크루가 닫힌 범위에서 잠긴 경로로 바로 들어오면 안내를 띄우고 확인하면 뒤로 돌린다`() {
        show(ReleaseScope.FIRST_RELEASE)

        compose.onNodeWithText("크루 알림 열기").performClick()
        compose.onNodeWithText("추후 추가 예정").assertExists()
        compose.onNodeWithText("크루 홈 화면").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("홈 화면 크루 공개 false").assertExists()
    }

    @Test
    fun `TC-9697 잠그지 않는 빌드는 크루가 열리고 화면 숨김 판정도 열려 있다`() {
        show(ReleaseScope.of(isLockingUnreleased = false))

        compose.onNodeWithText("홈 화면 크루 공개 true").assertExists()
        compose.onNodeWithText("크루").performClick()
        compose.onNodeWithText("크루 화면").assertExists()
    }

    private fun show(releaseScope: ReleaseScope) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkNavHost(
                    featureEntries = setOf(LockEntries),
                    startRoute = Routes.HOME,
                    onExit = {},
                    loggedIn = true,
                    releaseScope = releaseScope
                )
            }
        }
    }
}
