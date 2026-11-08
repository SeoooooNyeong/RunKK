package com.a207.runkk.app

import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 빌드 변형마다 실제 BuildConfig.LOCK_UNRELEASED로 고른 배포 범위(S15P31A207-248). prod·develop 변형(testProdDebugUnitTest)은 잠그는
 * 빌드라 크루·아바타 꾸미기·런바디만 열리고 카드 뒷면·Runimal 공유는 닫힌다. local 변형은 모두 열린다.
 */
class ReleaseBuildScopeTest {
    private val scope = ReleaseScope.of(BuildConfig.LOCK_UNRELEASED)

    @Test
    fun `크루·아바타 꾸미기·런바디는 어느 변형에서나 열리고 경로·탭도 막지 않는다`() {
        listOf(UnreleasedFeature.CREW, UnreleasedFeature.AVATAR_CUSTOMIZE, UnreleasedFeature.RUNBODY).forEach {
            assertTrue(scope.isOpen(it), "$it")
        }
        listOf(Routes.CREW, Routes.SHOP, Routes.AVATAR, Routes.REPORT).forEach {
            assertFalse(BetaLock.isLocked(Routes.Key(it), scope), it)
        }
        assertEquals(emptySet<RunkkTab>(), BetaLock.lockedTabs(scope))
    }

    @Test
    fun `카드 뒷면·Runimal 공유는 잠그는 빌드에서만 닫힌다`() {
        val isLocking = BuildConfig.LOCK_UNRELEASED
        assertEquals(!isLocking, scope.isOpen(UnreleasedFeature.CARD_BACK))
        assertEquals(!isLocking, scope.isOpen(UnreleasedFeature.RUNIMAL_SHARE))
    }
}
