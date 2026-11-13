package com.a207.runkk.app.ui.runimal

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun entryOf(type: String) = typeBoard.types.first { it.type == type }

/** 16유형(G-5) ViewModel. */
class RunimalTypesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeRunimal()

    @Test
    fun `D94 내 유형 칸은 진한 테두리와 굵은 글자, 확정 이력 없는 유형은 30%로 흐리다(G-5)`() {
        val mine = RunimalTypeCellLook.of(isMine = true, isDimmed = false)
        assertTrue(mine.isStrongBorder)
        assertTrue(mine.isBold)
        assertEquals(11.5f, mine.fontSizeSp)
        assertEquals(1f, mine.alpha)
        val other = RunimalTypeCellLook.of(isMine = false, isDimmed = false)
        assertFalse(other.isStrongBorder)
        assertFalse(other.isBold)
        assertEquals(10.5f, other.fontSizeSp)
        assertEquals(0.3f, RunimalTypeCellLook.of(isMine = false, isDimmed = true).alpha)
    }

    @Test
    fun `TC-9738 16유형 격자는 서버 순서를 열 단위로 놓는다(G-5, R2-14)`() {
        val rows = columnMajorRows(typeBoard.types.map { it.type }, columns = 4)
        assertEquals(
            listOf("MORNING_SPARROW", "COMMUTER_TURTLE", "WALKING_PUPPY", "PILLAR_ELEPHANT"),
            rows[0],
            "1행: 각 열의 첫 유형"
        )
        assertEquals(
            listOf("STROLLING_CAT", "LONE_WOLF", "PICNIC_RABBIT", "WILD_HORSE"),
            rows[3],
            "4행: 각 열의 마지막 유형"
        )
        assertEquals(
            listOf("MORNING_SPARROW", "WHEEL_HAMSTER", "ALLEY_SQUIRREL", "STROLLING_CAT"),
            rows.map { it[0] },
            "1열: 아침 참새, 쳇바퀴 햄스터, 골목 탐험 다람쥐, 마실 나온 고양이"
        )
        assertEquals(listOf(listOf("A", "C"), listOf("B", null)), columnMajorRows(listOf("A", "B", "C"), columns = 2))
        assertEquals(emptyList(), columnMajorRows(emptyList<String>(), columns = 4))
    }

    @Test
    fun `TC-9738 지금 유형과 예전 확정 유형은 흐리지 않고 한 번도 확정받지 않은 유형은 흐린다`() {
        val s = RunimalTypesViewModel(repo).state.value
        assertFalse(s.isLoading)
        assertEquals(16, s.board?.types?.size)
        assertFalse(s.isDimmed(entryOf("COMMUTER_TURTLE")))
        assertFalse(s.isDimmed(entryOf("MORNING_SPARROW")))
        assertFalse(s.isDimmed(entryOf("STEADY_CAMEL")))
        assertTrue(s.isDimmed(entryOf("PENGUIN_SQUAD")))
        assertEquals(13, s.board!!.types.count(s::isDimmed))
        // 흐림은 확정 이력만 본다(D-285). 확정 이력이 없는 유형은 지금 유형이어도 흐리다.
        assertTrue(s.isDimmed(entryOf("COMMUTER_TURTLE").copy(isEverConfirmed = false)))
    }

    @Test
    fun `TC-9738 머리 범례는 지금 유형이 기운 쪽을 안다, 유형이 없으면 비어 있다`() {
        assertEquals(turtleAxes, RunimalTypesViewModel(repo).state.value.currentAxes)
        repo.typesResult = Outcome.Success(typeBoard.copy(current = null))
        assertEquals(emptyMap(), RunimalTypesViewModel(repo).state.value.currentAxes)
    }

    @Test
    fun `16유형을 불러오지 못하면 오류를 보이고 다시 시도한다`() {
        repo.typesResult = Outcome.Failure(AppError.Network)
        val v = RunimalTypesViewModel(repo)
        assertNotNull(v.state.value.error)
        assertNull(v.state.value.board)
        repo.typesResult = Outcome.Success(typeBoard)
        v.load()
        assertNull(v.state.value.error)
        assertEquals(16, v.state.value.board?.types?.size)
    }
}

/** 유형 상세(G-6) ViewModel. */
class RunimalTypeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeRunimal()

    @Test
    fun `TC-9738 비율 33이면 러너 중 33%가 이 유형이에요를 보인다`() {
        val v = RunimalTypeViewModel(repo)
        v.load("COMMUTER_TURTLE")
        assertEquals(listOf("COMMUTER_TURTLE"), repo.typeCalls)
        assertEquals(turtleDetail, v.state.value.detail)
        assertEquals(uiText(R.string.runimal_type_share, 33), v.state.value.shareLabel)
    }

    @Test
    fun `TC-9738 비율이 null이면 비율 줄이 없다`() {
        repo.typeResult = Outcome.Success(turtleDetail.copy(sharePercent = null))
        val v = RunimalTypeViewModel(repo)
        v.load("COMMUTER_TURTLE")
        assertNull(v.state.value.shareLabel)
    }

    @Test
    fun `같은 유형을 다시 열면 다시 부르지 않고, 실패하면 다시 시도로 부른다`() {
        repo.typeResult = Outcome.Failure(AppError.Network)
        val v = RunimalTypeViewModel(repo)
        v.load("COMMUTER_TURTLE")
        assertNotNull(v.state.value.error)
        repo.typeResult = Outcome.Success(turtleDetail)
        v.load("COMMUTER_TURTLE", isForced = true)
        v.load("COMMUTER_TURTLE")
        assertEquals(2, repo.typeCalls.size)
        assertNull(v.state.value.error)
        assertEquals(turtleDetail, v.state.value.detail)
    }
}

private fun str(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

private val loadedTypes = RunimalTypesUiState(isLoading = false, board = typeBoard)
private val loadedType = RunimalTypeUiState(type = "COMMUTER_TURTLE", isLoading = false, detail = turtleDetail)

/** 16유형(G-5)·유형 상세(G-6) 화면. */
@RunWith(RobolectricTestRunner::class)
class RunimalTypesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `TC-9738 지금 유형은 내 유형, 한 번도 없는 유형은 흐림으로 읽고 흐린 유형도 누르면 상세로 간다`() {
        val selected = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunimalTypesScreen(loadedTypes, {}, { selected += it }, {}) }
        }
        compose.onNodeWithText(str(R.string.runimal_types_title)).assertExists()
        compose.onNodeWithText("출근도장 거북이").assert(hasState(str(R.string.runimal_my_type)))
        compose.onNodeWithText("아침 참새")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("펭귄 군단").assert(hasState(str(R.string.runimal_types_never_confirmed)))
        compose.onNodeWithText("펭귄 군단").performClick()
        compose.onNodeWithText("아침 참새").performClick()
        assertEquals(listOf("PENGUIN_SQUAD", "MORNING_SPARROW"), selected)
    }

    @Test
    fun `머리 범례 4칸을 보인다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { RunimalTypesScreen(loadedTypes, {}, {}, {}) } }
        listOf("솔로", "소셜", "단거리", "장거리", "단골", "탐험", "루틴", "즉흥").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun `16유형을 불러오지 못하면 다시 시도를 누를 수 있다`() {
        var retried = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunimalTypesScreen(
                    RunimalTypesUiState(isLoading = false, error = uiText(R.string.runimal_load_failed)),
                    {},
                    {},
                    { retried++ }
                )
            }
        }
        compose.onNodeWithText(str(R.string.runimal_load_failed)).assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `TC-9738 유형 상세는 이름, 기운 쪽 4축 칩, 특징 3줄, 러너 비율을 보인다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { RunimalTypeScreen(loadedType, {}, {}) } }
        compose.onNodeWithText(str(R.string.runimal_type_title)).assertExists()
        compose.onNodeWithText("출근도장 거북이").assertExists()
        listOf("솔로", "장거리", "단골", "루틴").forEach { compose.onNodeWithText(it).assertExists() }
        listOf("소셜", "단거리", "탐험", "즉흥").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("같은 길을 같은 시간에, 오래 꾸준히 달려요").assertExists()
        compose.onNodeWithText(str(R.string.runimal_type_traits_title)).assertExists()
        compose.onNodeWithText("혼자 정해 둔 코스를 반복해서 달려요").assertExists()
        compose.onNodeWithText("비슷한 요일과 시간대에 꾸준히 기록을 남겨요").assertExists()
        compose.onNodeWithText("러너 중 33%가 이 유형이에요").assertExists()
    }

    @Test
    fun `D101 16유형 제목은 바 가운데에 있다(G-5)`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { RunimalTypesScreen(loadedTypes, {}, {}, {}) } }
        compose.assertTitleCentered(str(R.string.runimal_types_title))
    }

    @Test
    fun `D101 유형 상세 제목은 바 가운데에 있다(G-6)`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { RunimalTypeScreen(loadedType, {}, {}) } }
        compose.assertTitleCentered(str(R.string.runimal_type_title))
    }

    @Test
    fun `TC-9738 러너 비율이 null이면 그 줄이 없다`() {
        val noShare = loadedType.copy(detail = turtleDetail.copy(sharePercent = null))
        compose.setContent { RunkkTheme(reduceMotion = true) { RunimalTypeScreen(noShare, {}, {}) } }
        compose.onNodeWithText("출근도장 거북이").assertExists()
        compose.onNodeWithText("러너 중", substring = true).assertDoesNotExist()
    }
}

/** 16유형(G-5)·유형 상세(G-6) 스크린샷. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunimalTypesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun types() = compose.captureRunkkScreen("G5_runimal_types") { RunimalTypesScreen(loadedTypes, {}, {}, {}) }

    @Test
    fun type() = compose.captureRunkkScreen("G6_runimal_type") { RunimalTypeScreen(loadedType, {}, {}) }
}

/** [title] 제목(heading)의 가로 가운데가 화면 가운데에 있는지 본다(D101). */
internal fun ComposeContentTestRule.assertTitleCentered(title: String) {
    val screen = onRoot().getUnclippedBoundsInRoot()
    val bar = onNode(hasText(title) and isHeading()).getUnclippedBoundsInRoot()
    assertEquals((screen.left + screen.right).value / 2f, (bar.left + bar.right).value / 2f, 1f, "$title 제목 가운데")
}
