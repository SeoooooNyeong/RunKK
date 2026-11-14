package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.avatar.AVATAR_LOADING_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillFallback
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillOnReady
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.SlotRunnerLook
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
 * 로그인한 동안 저장소를 데워 두며 로그아웃(Overlay가 빠짐)에 비운다. 준비 전에는 홈·러닝 중 칸 모두 가운데 로딩 표시를 보이고, 러닝 중
 * 칸은 준비에 실패했을 때만 지금 달리기 그림을 보인다([임시:D-성-181]).
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
        prepare = { _, _ -> },
        scope = scope
    )
    private val resolver = RunnerLookResolver(repo, headsOf(repo))
    private val entry = AvatarFeatureEntry(store, resolver)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `ui·avatar가 홈 아바타 칸과 러닝 중 아바타 칸과 전신 정지 그림 칸을 채운다`() {
        assertEquals(setOf(SlotKey.HOME_AVATAR, SlotKey.RUN_AVATAR, SlotKey.AVATAR_STILL), entry.slots.keys)
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
    fun `러닝 중 칸은 준비 전이면 지금 달리기 그림 대신 가운데 로딩 표시를 보인다(D-성-181)`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalSlotAvatarPose provides AvatarSlotPose.RUN) {
                    Box(Modifier.size(88.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(50)
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "100ms 안에는 그리지 않는다")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertExists()
        compose.onAllNodesWithTag(RUN_AVATAR_FALLBACK_TAG).assertCountEqualsTo(0, "기존 그림이 먼저 보였다가 바뀌지 않는다")
    }

    @Test
    fun `러닝 중 칸은 준비가 8초 넘게 끝나지 않으면(성별을 모름 등) 로딩 표시 대신 지금 달리기 그림을 보인다(D-성-181, 리뷰 U-11)`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalSlotAvatarPose provides AvatarSlotPose.RUN) {
                    Box(Modifier.size(88.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(7_900)
        compose.onAllNodesWithTag(RUN_AVATAR_FALLBACK_TAG).assertCountEqualsTo(0, "8초 전에는 기다린다")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(RUN_AVATAR_FALLBACK_TAG).assertExists()
    }

    @Test
    fun `러닝 중 칸은 준비에 실패하면 로딩 표시를 거두고 지금 달리기 그림을 보인다(D-성-181)`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        gender.value = Gender.MALE
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalSlotAvatarPose provides AvatarSlotPose.RUN) {
                    Box(Modifier.size(88.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Failed }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(RUN_AVATAR_FALLBACK_TAG).assertExists()
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "실패면 로딩 표시 없음")
    }

    @Test
    fun `러닝 중 칸은 상대 대상이면 내 아바타 저장소를 쓰지 않고 상대를 그린다(D-성-259)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = false) {
                CompositionLocalProvider(
                    LocalSlotAvatarPose provides AvatarSlotPose.RUN,
                    LocalSlotAvatarRunSubject provides RunAvatarSubject.other(null, isFemale = true)
                ) {
                    Box(Modifier.size(76.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(emptyList(), repo.calls.filter { it == "runner" }, "상대 칸은 내 아바타를 준비하지 않는다")
        assertEquals(emptyList(), repo.calls.filter { it.startsWith("shop") }, "모습이 없으면 상점 목록을 받지 않는다")
    }

    @Test
    fun `러닝 중 칸은 움직임 줄이기면 모습을 받지 못한 상대를 멈춘 그림으로 그린다(D-성-262)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalSlotAvatarPose provides AvatarSlotPose.RUN,
                    LocalSlotAvatarRunSubject provides RunAvatarSubject.other(null, isFemale = false)
                ) {
                    Box(Modifier.size(76.dp)) { entry.slots.getValue(SlotKey.RUN_AVATAR)() }
                }
            }
        }
        // 시험 환경은 Rive를 그리지 못하므로 멈춘 그림을 시도한 뒤 성별 기본 달리기 그림으로 바뀐다.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(RUN_AVATAR_STILL_TAG).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(RUN_AVATAR_FALLBACK_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(AVATAR_LOADING_TAG).assertCountEqualsTo(0, "움직임 줄이기에서는 Rive 무대를 돌리지 않는다")
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

    @Test
    fun `전신 정지 그림 칸은 내 준비에 실패하면 성별 기본 전신 그림을 보인다`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        gender.value = Gender.FEMALE
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalSlotAvatarStill provides AvatarStillSubject(isMine = true)) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Failed }
        compose.onNodeWithTag(AVATAR_STILL_FALLBACK_TAG).assertExists()
    }

    @Test
    fun `전신 정지 그림 칸은 다른 러너를 Rive로 그리지 못하면(시험 환경) 성별 기본 전신 그림을 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalSlotAvatarStill provides AvatarStillSubject(isMine = false, isFemale = false)
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(AVATAR_STILL_FALLBACK_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `전신 정지 그림 칸은 받은 모습이 있으면 그 모습의 재료(상점 목록)를 읽어 그린다(D-성-272)`() {
        val look = SlotRunnerLook(isFemale = true, topItemId = 1, bodyVariant = 1, runAmount = 30)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalSlotAvatarStill provides AvatarStillSubject(isMine = false, isFemale = true, look = look)
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        // 시험 환경은 Rive를 그리지 못해 성별 기본 전신 그림으로 끝난다. 그 전에 받은 모습의 의상을 찾으려 상점 목록을 읽는다.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(AVATAR_STILL_FALLBACK_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("shop:null"), repo.calls.filter { it.startsWith("shop") })
    }

    @Test
    fun `전신 정지 그림 칸은 아바타 꾸미기가 닫혀 있으면 부르는 쪽 지금 그림을 그린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalReleaseScope provides ReleaseScope.FIRST_RELEASE,
                    LocalSlotAvatarStill provides AvatarStillSubject(isMine = true),
                    LocalSlotAvatarStillFallback provides { Box(Modifier.testTag("caller-fallback")) }
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.onNodeWithTag("caller-fallback").assertExists()
        compose.onAllNodesWithTag(AVATAR_STILL_FALLBACK_TAG).assertCountEqualsTo(0, "기능 쪽 기본 그림은 그리지 않는다")
    }

    @Test
    fun `전신 정지 그림 칸은 성별 기본 전신 그림(준비 실패)에서도 준비 신호를 보낸다(D-성-292)`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        gender.value = Gender.FEMALE
        var readies = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalSlotAvatarStill provides AvatarStillSubject.MINE,
                    LocalSlotAvatarStillOnReady provides { readies++ }
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 2_000) { store.state.value is MyAvatarState.Failed }
        compose.onNodeWithTag(AVATAR_STILL_FALLBACK_TAG).assertExists()
        assertEquals(1, readies)
    }

    @Test
    fun `전신 정지 그림 칸은 내 모습을 기다리는 동안에는 준비 신호를 보내지 않는다`() {
        var readies = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalSlotAvatarStill provides AvatarStillSubject.MINE,
                    LocalSlotAvatarStillOnReady provides { readies++ }
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, readies, "성별을 기다리는 중")
        // 준비가 끝나지 않으면 시간 초과 뒤 성별 기본 그림으로 끝낸다(신호는 반드시 온다).
        compose.mainClock.advanceTimeBy(AVATAR_STILL_READY_TIMEOUT_MS)
        compose.onNodeWithTag(AVATAR_STILL_FALLBACK_TAG).assertExists()
        assertEquals(1, readies)
    }

    @Test
    fun `전신 정지 그림 칸은 꾸미기가 닫혀 부르는 쪽 그림을 그릴 때도 준비 신호를 보낸다`() {
        var readies = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalReleaseScope provides ReleaseScope.FIRST_RELEASE,
                    LocalSlotAvatarStill provides AvatarStillSubject.MINE,
                    LocalSlotAvatarStillFallback provides { Box(Modifier.testTag("caller-fallback")) },
                    LocalSlotAvatarStillOnReady provides { readies++ }
                ) {
                    Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
                }
            }
        }
        compose.onNodeWithTag("caller-fallback").assertExists()
        assertEquals(1, readies)
    }

    @Test
    fun `전신 정지 그림 칸은 칸 밖(사람을 넘기지 않음)이면 아무것도 그리지 않는다`() {
        compose.setContent {
            Box(Modifier.size(80.dp, 128.dp)) { entry.slots.getValue(SlotKey.AVATAR_STILL)() }
        }
        compose.onAllNodesWithTag(AVATAR_STILL_FALLBACK_TAG).assertCountEqualsTo(0, "사람이 없으면 비운다")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsTo(
    count: Int,
    message: String
) {
    assertEquals(count, fetchSemanticsNodes().size, message)
}
