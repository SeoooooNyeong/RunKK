package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 크루 정보 수정(F-26)의 크루명 규칙(결정 C1, 서버 S2·S3와 같다): 기존 이름 길이만큼은 입력을 받아 긴 옛 이름도 지워 줄일 수 있고,
 * 이름을 그대로 두면 길이와 무관하게 통과하며, 바꿀 때만 NAME_MIN~NAME_MAX(12자)로 검사한다.
 */
class CrewEditNameRuleTest {
    private val longOriginal = "가".repeat(CrewFormState.NAME_MAX + 3)

    @Test
    fun `기존 이름이 상한보다 길면 그 길이만큼 입력을 받는다`() {
        assertEquals(CrewFormState.NAME_MAX + 3, editNameMaxLength(longOriginal))
        assertEquals(CrewFormState.NAME_MAX, editNameMaxLength("새벽질주"))
    }

    @Test
    fun `이름을 그대로 두면 상한보다 길어도 통과한다`() {
        val form = CrewFormState(name = longOriginal, tags = setOf("WEEKDAY_DAWN"))
        assertTrue(form.isEditNameValid(longOriginal))
        assertTrue(form.isEditValid(longOriginal))
    }

    @Test
    fun `이름을 바꾸면 상한 안이어야 하고 줄여서 상한 안에 들면 통과한다`() {
        val stillLong = CrewFormState(name = longOriginal.dropLast(1), tags = setOf("WEEKDAY_DAWN"))
        assertFalse(stillLong.isEditNameValid(longOriginal))
        val shortened = stillLong.copy(name = "가".repeat(CrewFormState.NAME_MAX))
        assertTrue(shortened.isEditNameValid(longOriginal))
        assertFalse(shortened.copy(name = "가").isEditNameValid(longOriginal))
    }

    @Test
    fun `태그가 없으면 이름과 무관하게 저장할 수 없다`() {
        assertFalse(CrewFormState(name = longOriginal).isEditValid(longOriginal))
    }
}

/** 수정 저장은 같은 규칙을 따른다(긴 옛 이름을 그대로 두면 저장, 바꿔서 너무 길면 보내지 않음). */
class CrewEditNameViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm() = CrewHomeViewModel(
        repo,
        ServerClock(MutableClock()),
        FakeCelebrationSharer(),
        { _, _ -> Outcome.Success(Unit) }
    ).also { it.bind(Routes.CrewHome(11)) }

    @Test
    fun `긴 옛 이름을 그대로 두고 저장하면 보내고, 바꿔서 상한을 넘으면 보내지 않는다`() {
        val long = "가".repeat(CrewFormState.NAME_MAX + 3)
        repo.role = CrewRole.LEADER
        repo.renameTo(long)
        val vm = vm()
        vm.onPage(CrewHomePage.EDIT)
        assertEquals(long, vm.state.value.editForm.name)
        vm.onEditForm(vm.state.value.editForm.copy(name = long.dropLast(1)))
        vm.onEditSave()
        assertTrue(repo.calls.none { it.startsWith("changeInfo") })
        vm.onEditForm(vm.state.value.editForm.copy(name = long))
        vm.onEditSave()
        assertTrue("changeInfo:$long" in repo.calls)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewEditNameScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun edit(name: String, original: String) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = CrewRole.LEADER).copy(name = original),
        page = CrewHomePage.EDIT,
        editForm = crewDetail().toFormState().copy(name = name)
    )

    @Test
    fun `바꾼 이름이 너무 길 때만 안내하고 그대로 둔 긴 이름은 안내하지 않는다`() {
        val long = "가".repeat(CrewFormState.NAME_MAX + 3)
        var state by mutableStateOf(edit(long, long))
        compose.setContent { RunkkTheme(reduceMotion = true) { CrewHomeScreen(state, CrewHomeActions()) } }
        val hint = "크루명은 ${CrewFormState.NAME_MIN}~${CrewFormState.NAME_MAX}자로 입력해 주세요"
        compose.onNodeWithText(hint).assertDoesNotExist()
        state = edit(long.dropLast(1), long)
        compose.waitForIdle()
        compose.onNodeWithText(hint).assertExists()
    }
}
