package com.a207.runkk.app.ui.runimal

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.domain.runimal.RunimalStatus
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.RunimalCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import com.a207.runkk.app.ui.share.ShareTarget
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private val card = RunimalCard(
    type = "SPARROW",
    title = "아침 참새",
    description = "매일 같은 시간, 동네 한 바퀴 가볍게",
    axisRatios = linkedMapOf("솔로" to 0.78, "단거리" to 0.62, "단골길" to 0.7, "루틴" to 0.66)
)

private val types = listOf(
    "아침 참새", "쳇바퀴 햄스터", "골목 탐험 다람쥐", "마실 나온 고양이", "출근도장 거북이", "묵묵한 낙타",
    "하늘길 독수리", "고독한 늑대", "산책 나온 강아지", "놀이터 원숭이", "탐험대 수달", "소풍 나온 토끼",
    "크루의 기둥 코끼리", "펭귄 군단", "V자 리더 철새", "들판의 야생마"
)

private val confirmed =
    RunimalStatus("SPARROW", isProvisional = false, remainingRuns = 4, card = card, allTypes = types)

private class FakeRunimal : RunimalRepository {
    var status: Outcome<RunimalStatus> = Outcome.Success(confirmed)
    var cardResult: Outcome<RunimalCard> = Outcome.Success(card)
    var cardCalls = 0

    override suspend fun status() = status

    override suspend fun card(): Outcome<RunimalCard> {
        cardCalls++
        return cardResult
    }
}

private class RecordingTarget(val name: String, val calls: MutableList<String>) : ShareTarget {
    override fun share(context: Context, image: Uri): ShareOutcome {
        calls += name
        return if (name == "save") ShareOutcome.Saved else ShareOutcome.Shared
    }
}

private class Settings : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = true

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** MY Runimal ViewModel(카드 그리기에 Bitmap이 필요해 Robolectric 위에서 돈다). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunimalViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRunimal()
    private val shared = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(): RunimalViewModel {
        val context = RuntimeEnvironment.getApplication()
        val sharer = CardSharer(context, Settings()) { _, file -> Uri.fromFile(file) }
        val targets = RunimalShareTargets(
            RecordingTarget("save", shared),
            RecordingTarget("system", shared),
            RecordingTarget("story", shared)
        )
        return RunimalViewModel(repo, RunimalCardRenderer(), sharer, targets, dispatcher)
    }

    /** CardSharer는 파일 쓰기를 Dispatchers.IO에서 하므로 공유가 끝날 때까지 기다린다. */
    private fun RunimalViewModel.awaitShared() = runBlocking {
        withTimeout(10_000) { state.first { !it.isSharing } }
    }

    @Test
    fun `확정 유형과 다음 확정까지 남은 횟수를 보인다(FR-151·FR-154)`() {
        val s = vm().state.value
        assertFalse(s.isLoading)
        assertEquals("아침 참새", s.status?.card?.title)
        assertEquals(uiText(R.string.runimal_remaining_confirm, 4), s.remainingLabel)
        assertTrue(s.canShare)
    }

    @Test
    fun `유형이 없으면 임시 유형까지 남은 횟수를 보이고 공유하지 않는다`() {
        repo.status = Outcome.Success(RunimalStatus(null, true, 2, null, types))
        val v = vm()
        assertEquals(uiText(R.string.runimal_remaining_provisional, 2), v.state.value.remainingLabel)
        v.onShare(RunimalShareKind.SAVE)
        assertEquals(0, repo.cardCalls)
    }

    @Test
    fun `카드 공유 - 카드를 그려 고른 대상으로 보내고, 다시 공유하면 그린 이미지를 다시 쓴다(FR-168)`() {
        val v = vm()
        v.onShare(RunimalShareKind.SAVE)
        v.awaitShared()
        assertEquals(listOf("save"), shared)
        assertEquals(UiText.Res(R.string.runimal_saved_to_gallery), v.state.value.message)
        assertNotNull(v.state.value.cardImage)
        v.onMessageShown()
        v.onShare(RunimalShareKind.STORY)
        v.awaitShared()
        assertEquals(listOf("save", "story"), shared)
        assertEquals(1, repo.cardCalls)
        assertNull(v.state.value.message)
    }

    @Test
    fun `다시 불러오면 그려 둔 카드를 버린다(유형이 바뀌었을 수 있다)`() {
        val v = vm()
        v.onShare(RunimalShareKind.SAVE)
        v.awaitShared()
        assertNotNull(v.state.value.cardImage)
        v.load()
        assertNull(v.state.value.cardImage)
    }

    @Test
    fun `유형 카드가 거부되면 안내한다`() {
        repo.cardResult = Outcome.Failure(AppError.Rejected("NO_TYPE"))
        val v = vm()
        v.onShare(RunimalShareKind.SYSTEM)
        assertEquals(UiText.Res(R.string.runimal_share_no_type), v.state.value.message)
        assertFalse(v.state.value.isSharing)
    }

    @Test
    fun `불러오지 못하면 오류를 보이고 다시 시도한다`() {
        repo.status = Outcome.Failure(AppError.Network)
        val v = vm()
        assertNotNull(v.state.value.error)
        repo.status = Outcome.Success(confirmed)
        v.load()
        assertNull(v.state.value.error)
    }
}

private fun str(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

/** MY Runimal 화면: 공유 버튼과 축 비율 라벨. */
@RunWith(RobolectricTestRunner::class)
class RunimalScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `공유 버튼 세 개와 축별 우세 비율을 읽어 준다`() {
        val calls = mutableListOf<RunimalShareKind>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, { calls += it }, {}, {})
            }
        }
        compose.onNodeWithContentDescription(str(R.string.runimal_axis_description, "솔로", 78)).assertExists()
        compose.onNodeWithText(
            str(R.string.runimal_share_with, str(R.string.runimal_share_story))
        ).performClick()
        compose.onNodeWithText(str(R.string.runimal_share_save)).performClick()
        compose.onNodeWithText(str(R.string.runimal_share_system)).performClick()
        assertEquals(listOf(RunimalShareKind.STORY, RunimalShareKind.SAVE, RunimalShareKind.SYSTEM), calls)
    }
}

/** MY Runimal 스크린샷(E1). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunimalScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmedType() = compose.captureRunkkScreen("E1_runimal_type_card") {
        RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {})
    }

    @Test
    fun noTypeYet() = compose.captureRunkkScreen("E1_runimal_no_type") {
        RunimalScreen(RunimalUiState(isLoading = false, status = RunimalStatus(null, true, 2, null, types)), {
        }, {}, {}, {})
    }
}
