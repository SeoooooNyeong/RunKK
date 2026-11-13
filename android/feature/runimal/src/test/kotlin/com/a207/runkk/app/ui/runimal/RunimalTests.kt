package com.a207.runkk.app.ui.runimal

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.ui.share.CapturedCard
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.RunimalCardRenderer
import com.a207.runkk.app.ui.share.ShareImageFramer
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
    type = "MORNING_SPARROW",
    title = "아침 참새",
    description = "매일 같은 시간, 동네 한 바퀴 가볍게",
    axisRatios = linkedMapOf("COMPANION" to 0.78, "DISTANCE" to 0.62, "COURSE" to 0.7, "HABIT" to 0.66)
)

/** 서버는 16유형을 RunimalType enum 이름으로 보낸다(RunimalController.StatusResponse.allTypes). */
private val types = RunimalArt.entries.filter { it != RunimalArt.UNKNOWN }.map { it.name }

private val confirmed =
    RunimalStatus("MORNING_SPARROW", isProvisional = false, remainingRuns = 4, card = card, allTypes = types)

/** 서버가 축 막대(axes)를 보내는 카드: 솔로런 78%, 장거리 64%, 단골길 71%, 루틴런 58%. */
private val cardWithAxes = card.copy(
    axes = listOf(
        RunimalCard.AxisBar("COMPANION", AxisLean.A, 78),
        RunimalCard.AxisBar("DISTANCE", AxisLean.B, 64),
        RunimalCard.AxisBar("COURSE", AxisLean.A, 71),
        RunimalCard.AxisBar("HABIT", AxisLean.A, 58)
    )
)

private val confirmedWithAxes = confirmed.copy(card = cardWithAxes)

private class RecordingTarget(val name: String, val calls: MutableList<String>) : ShareTarget {
    /** 마지막으로 받은 이미지 높이(시험은 파일 주소를 넘기므로 파일을 읽어 잰다). */
    var lastHeight = 0

    override fun share(context: Context, image: Uri): ShareOutcome {
        calls += name
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(image.path, options)
        lastHeight = options.outHeight
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

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** MY Runimal ViewModel(카드 그리기에 Bitmap이 필요해 Robolectric 위에서 돈다). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunimalViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRunimal(status = Outcome.Success(confirmed), cardResult = Outcome.Success(card))
    private val shared = mutableListOf<String>()
    private val save = RecordingTarget("save", shared)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(): RunimalViewModel {
        val context = RuntimeEnvironment.getApplication()
        val sharer = CardSharer(context, Settings()) { _, file -> Uri.fromFile(file) }
        val targets = RunimalShareTargets(
            save,
            RecordingTarget("system", shared),
            RecordingTarget("story", shared)
        )
        return RunimalViewModel(
            repo,
            RunimalCardRenderer(context),
            sharer,
            targets,
            ShareImageFramer(context),
            dispatcher
        )
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
    fun `공유 단계는 유형이 있을 때만 열리고, 찍은 카드는 아래에 워드마크를 붙여 보낸다(K-3)`() {
        val v = vm()
        v.onShareStep(true)
        assertTrue(v.state.value.isShareStep)
        val captured = android.graphics.Bitmap.createBitmap(334, 400, android.graphics.Bitmap.Config.ARGB_8888)
        v.onShare(RunimalShareKind.SAVE, CapturedCard(captured, isDark = true))
        v.awaitShared()
        assertEquals(listOf("save"), shared)
        assertTrue(save.lastHeight > captured.height, "워드마크 칸 ${save.lastHeight}")
        assertNull(v.state.value.cardImage, "찍은 카드는 렌더러 이미지로 남기지 않는다")
        v.onShareStep(false)
        assertFalse(v.state.value.isShareStep)
    }

    @Test
    fun `유형이 없으면 공유 단계를 열지 않는다`() {
        repo.status = Outcome.Success(RunimalStatus(null, true, 2, null, types))
        val v = vm()
        v.onShareStep(true)
        assertFalse(v.state.value.isShareStep)
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
    fun `오른쪽 위 공유로 공유 단계를 열고, 공유 단계는 공유 대상 세 개로 찍은 카드를 넘긴다(G-4, K-3)`() {
        val steps = mutableListOf<Boolean>()
        val calls = mutableListOf<RunimalShareKind>()
        val captured = mutableListOf<CapturedCard?>()
        var state by mutableStateOf(RunimalUiState(isLoading = false, status = confirmed))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(
                    state,
                    {},
                    {},
                    {},
                    {},
                    {},
                    onShareStep = { steps += it },
                    onShareCaptured = { kind, card ->
                        calls += kind
                        captured += card
                    }
                )
            }
        }
        compose.onNodeWithText(str(R.string.runimal_share)).performClick()
        assertEquals(listOf(true), steps)
        state = state.copy(isShareStep = true)
        compose.onNodeWithText(str(R.string.runimal_share_title)).assertExists()
        compose.onRoot().captureToImage()
        compose.onNodeWithText("인스타그램 스토리").performClick()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.onNodeWithText("더 보기").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RunimalShareKind.STORY, RunimalShareKind.SAVE, RunimalShareKind.SYSTEM), calls)
        assertTrue(captured.all { it != null && it.bitmap.width > 0 })
    }

    @Test
    fun `축별 우세 비율을 읽어 주고 하단 공유 막대는 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription(str(R.string.runimal_axis_description, "동행", 78)).assertExists()
        // 내 유형 카드의 큰 그림과 16유형 목록 칸이 동물 이름으로 읽힌다.
        compose.onAllNodesWithContentDescription("아침 참새").assertCountEquals(2)
        compose.onNodeWithContentDescription("펭귄 군단").assertExists()
        compose.onNodeWithText(str(R.string.runimal_share_save)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.runimal_share)).assertExists()
    }

    @Test
    fun `유형이 없으면 오른쪽 위 공유가 없다(K-3)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(
                    RunimalUiState(isLoading = false, status = RunimalStatus(null, true, 2, null, types)),
                    {},
                    {},
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText(str(R.string.runimal_share)).assertDoesNotExist()
    }

    @Test
    fun `유형 카드에 확정 유형 배지가 없고 임시 유형만 배지로 알린다(G-4, R2-19)`() {
        var state by mutableStateOf(RunimalUiState(isLoading = false, status = confirmed))
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunimalScreen(state, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("확정 유형").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.runimal_type_provisional)).assertDoesNotExist()
        state = RunimalUiState(isLoading = false, status = confirmed.copy(isProvisional = true))
        compose.onNodeWithText(str(R.string.runimal_type_provisional)).assertExists()
    }

    @Test
    fun `1차 배포 범위에서도 Runimal 공유가 열려 있고 다음 확정까지 남은 횟수가 보인다(S15P31A207-239)`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("다음 확정까지 4회").assertExists()
        compose.onNodeWithText(str(R.string.runimal_share)).assertExists()
    }

    @Test
    fun `K-3 공유 카드는 MY Runimal 꼬리표와 임시일 때만 임시 유형 꼬리표, 우세 쪽 축 막대를 보인다`() {
        var provisional by mutableStateOf(false)
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunimalShareCard(cardWithAxes, isProvisional = provisional) }
        }
        compose.onNodeWithText(str(R.string.runimal_title)).assertExists()
        compose.onNodeWithText(str(R.string.runimal_type_provisional)).assertDoesNotExist()
        compose.onNodeWithText("아침 참새", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(str(R.string.runimal_axis_description, "장거리", 64)).assertExists()
        provisional = true
        compose.onNodeWithText(str(R.string.runimal_type_provisional)).assertExists()
    }

    @Test
    fun `D101 MY Runimal 제목은 바 가운데에 있다(G-4)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {}, {})
            }
        }
        compose.assertTitleCentered(str(R.string.runimal_title))
    }

    @Test
    fun `TC-9878 16유형 버튼을 누르면 16유형 화면으로 가는 콜백을 부른다`() {
        var shown = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {}, { shown++ })
            }
        }
        compose.onNodeWithText("16유형 ›").performScrollTo().performClick()
        assertEquals(1, shown)
        // AN8-17: 보조 기술은 "›" 대신 읽을 이름을 읽는다.
        compose.onNodeWithContentDescription("16유형 보기").assertExists()
    }

    @Test
    fun `TC-9738 4축 막대는 우세한 쪽 이름과 비율을 읽고 그쪽에서부터 찬다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalScreen(RunimalUiState(isLoading = false, status = confirmedWithAxes), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(str(R.string.runimal_tendency_title)).assertExists()
        listOf("솔로런" to 78, "장거리" to 64, "단골길" to 71, "루틴런" to 58).forEach { (side, percent) ->
            compose.onNodeWithContentDescription(str(R.string.runimal_axis_description, side, percent))
                .assertExists()
        }
        // 서버 axes가 있으면 옛 축 비율 막대(동행 78%)는 그리지 않는다.
        compose.onNodeWithContentDescription(str(R.string.runimal_axis_description, "동행", 78)).assertDoesNotExist()
        // A 우세(솔로런)는 왼쪽에서, B 우세(장거리)는 오른쪽에서 찬다.
        val soloTrack = bounds("runimal_axis_track_COMPANION")
        val soloFill = bounds("runimal_axis_fill_COMPANION")
        assertEquals(soloTrack.left, soloFill.left, 1f)
        assertEquals(0.78f, soloFill.width / soloTrack.width, 0.02f)
        val distanceTrack = bounds("runimal_axis_track_DISTANCE")
        val distanceFill = bounds("runimal_axis_fill_DISTANCE")
        assertEquals(distanceTrack.right, distanceFill.right, 1f)
        assertEquals(0.64f, distanceFill.width / distanceTrack.width, 0.02f)
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}

/** MY 러니멀 스크린샷(E1): 유형 카드·유형 없음·공유 카드. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunimalScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmedType() = compose.captureRunkkScreen("E1_runimal_type_card") {
        RunimalScreen(RunimalUiState(isLoading = false, status = confirmed), {}, {}, {}, {}, {})
    }

    /** MY Runimal(G-4): 서버 축 막대, 16유형 버튼, 오른쪽 위 공유(1차 배포 범위에서도 연다). */
    @Test
    fun myRunimal() = compose.captureRunkkScreen("G4_my_runimal") {
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
            RunimalScreen(RunimalUiState(isLoading = false, status = confirmedWithAxes), {}, {}, {}, {}, {})
        }
    }

    /** K-3 1: Runimal 공유(확정 유형, 라이트). */
    @Test
    fun k3ShareConfirmed() = compose.captureRunkkScreen("K3_runimal_share_confirmed_light", isDarkTheme = false) {
        RunimalScreen(
            RunimalUiState(isLoading = false, status = confirmedWithAxes, isShareStep = true),
            {},
            {},
            {},
            {},
            {}
        )
    }

    /** K-3 2: Runimal 공유(임시 유형, 라이트). */
    @Test
    fun k3ShareProvisional() = compose.captureRunkkScreen("K3_runimal_share_provisional_light", isDarkTheme = false) {
        RunimalScreen(
            RunimalUiState(
                isLoading = false,
                status = confirmedWithAxes.copy(isProvisional = true),
                isShareStep = true
            ),
            {},
            {},
            {},
            {},
            {}
        )
    }

    /** K-3 1 다크: 테마 변수로 바뀐 공유 카드. */
    @Test
    fun k3ShareDark() = compose.captureRunkkScreen("K3_runimal_share_confirmed_dark") {
        RunimalScreen(
            RunimalUiState(isLoading = false, status = confirmedWithAxes, isShareStep = true),
            {},
            {},
            {},
            {},
            {}
        )
    }

    /** G-4 MY Runimal 라이트: 오른쪽 위 "공유". */
    @Test
    fun myRunimalLight() = compose.captureRunkkScreen("G4_my_runimal_light", isDarkTheme = false) {
        RunimalScreen(RunimalUiState(isLoading = false, status = confirmedWithAxes), {}, {}, {}, {}, {})
    }

    /** 공유 카드 이미지(ui.share RunimalCardRenderer)가 유형 그림과 한국어 축 이름을 넣어 그린다. */
    @Test
    fun shareCard() = compose.captureRunkkScreen("E1_runimal_share_card") {
        Image(
            RunimalCardRenderer(RuntimeEnvironment.getApplication())
                .render(card)
                .asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }

    @Test
    fun noTypeYet() = compose.captureRunkkScreen("E1_runimal_no_type") {
        RunimalScreen(RunimalUiState(isLoading = false, status = RunimalStatus(null, true, 2, null, types)), {
        }, {}, {}, {}, {})
    }
}
