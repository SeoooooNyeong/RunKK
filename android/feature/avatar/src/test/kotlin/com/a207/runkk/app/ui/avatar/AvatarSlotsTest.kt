package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.avatar.AVATAR_LOADING_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.SlotKey
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 칸 채움과 미리 준비(design-oo 05-30 [임시:D-성-100], [임시:D-성-104], [임시:D-성-106], 7절): ui.avatar가 홈·러닝 중 칸을 채우고, Overlay가
 * 로그인한 동안 저장소를 데워 두며 로그아웃(Overlay가 빠짐)에 비운다. 로딩 표시는 홈 칸에만 있고 러닝 중 칸은 지금 그림을 보인다.
 */
@RunWith(RobolectricTestRunner::class)
class AvatarSlotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAvatars()
    private val gender = MutableStateFlow<Gender?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = MyAvatarStore(
        avatars = repo,
        myGender = gender,
        heads = headsOf(repo),
        preload = {},
        prepare = {},
        scope = scope
    )
    private val entry = AvatarFeatureEntry(store)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `ui·avatar가 홈 아바타 칸과 러닝 중 아바타 칸을 채운다`() {
        assertEquals(setOf(SlotKey.HOME_AVATAR, SlotKey.RUN_AVATAR), entry.slots.keys)
        assertEquals(entry.slots, entry.slots, "칸 람다는 한 번 만들어 둔다(다시 그릴 때 칸 내용을 새로 만들지 않게)")
    }

    @Test
    fun `Overlay가 처음 그려지면 미리 준비를 시작하고, 빠져도(회전·구성 변경) 비우지 않는다(리뷰 R2-7)`() {
        var isShown by mutableStateOf(true)
        compose.setContent { if (isShown) entry.Overlay(null) }
        compose.waitForIdle()
        assertIs<MyAvatarState.Loading>(store.state.value, "성별을 기다린다")

        isShown = false
        compose.waitForIdle()
        assertIs<MyAvatarState.Loading>(store.state.value, "로그아웃은 세션 상태로만 비운다")
    }

    @Test
    fun `홈 칸은 준비 중이면 배경 자리와 가운데 로딩 표시를 보인다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(390.dp, 420.dp)) { entry.slots.getValue(SlotKey.HOME_AVATAR)() }
            }
        }
        compose.mainClock.advanceTimeBy(50)
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "100ms 안에는 그리지 않는다")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertExists()
        assertIs<MyAvatarState.Loading>(store.state.value, "칸이 먼저 그려져도 저장소를 데운다")
    }

    @Test
    fun `홈 칸은 준비에 실패하면 로딩 표시를 거두고 성별 기본 그림을 보인다`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        gender.value = Gender.FEMALE
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(390.dp, 420.dp)) { entry.slots.getValue(SlotKey.HOME_AVATAR)() }
            }
        }
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Failed }
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "실패면 로딩 표시 없음")
        compose.onNodeWithTag(AVATAR_STAGE_FALLBACK_TAG).assertExists()
    }

    @Test
    fun `러닝 중 칸은 준비 전이면 로딩 표시 없이 지금 달리기 그림을 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalSlotAvatarPose provides AvatarSlotPose.RUN) {
                    Box(Modifier.size(88.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(RUN_AVATAR_FALLBACK_TAG).assertExists()
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "러닝 중 칸은 로딩 표시 없음")
    }

    @Test
    fun `러닝 중 칸은 실패한 준비를 한 번 다시 시도한다`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        gender.value = Gender.MALE
        store.warm()
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Failed }

        repo.avatarResult = Outcome.Success(avatar)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(88.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
            }
        }
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Ready }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsTo(
    count: Int,
    message: String
) {
    assertEquals(count, fetchSemanticsNodes().size, message)
}
