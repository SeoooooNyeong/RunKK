package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.avatar.SelfieProgress
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 경로 기록 길잡이. */
private class RecordingNavigator : RunkkNavigator {
    val routes = mutableListOf<String>()

    override fun navigate(route: String) {
        routes += route
    }

    override fun navigate(key: NavKey) = Unit

    override fun back() = Unit

    override fun replace(key: NavKey) = Unit

    override fun selectTab(route: String) = Unit

    override fun restart(route: String) = Unit
}

/** 홈 칸 진행 배지(피그마 AV-7 4, design-oo 05-30 [임시:D-성-147]): 만드는 중일 때만 보이고 누르면 얼굴 만들기로 간다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class HomeSelfieBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAvatars()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checks = 0
    private val store = MyAvatarStore(
        repo,
        flowOf<Gender?>(Gender.MALE),
        headsOf(repo),
        {},
        { _, _ -> },
        scope,
        checkPending = { checks++ }
    )
    private val navigator = RecordingNavigator()

    @After
    fun tearDown() = scope.cancel()

    private fun show() = compose.setContent {
        RunkkTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalRunkkNavigator provides navigator) {
                Box(Modifier.size(390.dp, 420.dp)) { HomeAvatarSlotContent(store) }
            }
        }
    }

    @Test
    fun `만드는 중이면 배지를 띄우고 누르면 얼굴 만들기로 간다`() {
        repo.progress.value = SelfieProgress.Working("job-1")
        show()
        compose.onNodeWithTag(SELFIE_WORKING_BADGE_TAG).performClick()
        assertEquals(listOf(Routes.AVATAR_SETUP), navigator.routes)
    }

    @Test
    fun `홈 칸이 보이면 워커와 따로 기기 작업을 직접 확인한다(임시 D-성-188, D-성-223)`() {
        repo.progress.value = SelfieProgress.Working("job-1")
        show()
        compose.waitForIdle()
        // 칸이 보일 때(ON_START) 한 번, warm이 서버에서 들여온 작업 따라가기를 이은 직후 한 번
        assertEquals(2, checks)
    }

    @Test
    fun `만드는 중이 아니면 배지가 없고 끝나면 사라진다`() {
        show()
        compose.onNodeWithTag(SELFIE_WORKING_BADGE_TAG).assertDoesNotExist()
        repo.progress.value = SelfieProgress.Working("job-1")
        compose.onNodeWithTag(SELFIE_WORKING_BADGE_TAG).assertExists()
        repo.progress.value = SelfieProgress.Succeeded("job-1", "head:7:job-1")
        compose.onNodeWithTag(SELFIE_WORKING_BADGE_TAG).assertDoesNotExist()
    }

    @Test
    fun badge() {
        // 진행은 그리기 전에 정한다(컴포지션 안에서 StateFlow.value를 쓰지 않는다).
        repo.progress.value = SelfieProgress.Working("job-1")
        compose.captureRunkkScreen("home_avatar_slot_selfie_badge", isDarkTheme = false) { badgeSlot() }
    }

    @androidx.compose.runtime.Composable
    private fun badgeSlot() {
        CompositionLocalProvider(LocalRunkkNavigator provides navigator) {
            Box(Modifier.size(390.dp, 420.dp)) { HomeAvatarSlotContent(store) }
        }
    }
}
