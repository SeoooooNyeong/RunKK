package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 운영진 지정 확인(와이어프레임 M9, FR-204): 지정 전에 묻고, 지금 운영진 수를 보인다. */
@RunWith(RobolectricTestRunner::class)
class CrewStaffConfirmScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `확인 대화상자는 대상, 운영진 수, 취소와 지정을 보인다`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewStaffConfirmDialog(ManageAction.Appoint(members.members[1], 1), {
                    calls += "ok"
                }, { calls += "no" })
            }
        }
        compose.onNodeWithText("달빛님을 운영진으로 지정할까요?").assertExists()
        compose.onNodeWithText("운영진 1/2명").assertExists()
        compose.onNodeWithText("지정").performClick()
        assertEquals(listOf("ok"), calls)
    }
}

class CrewStaffConfirmViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `지정은 확인 뒤에 보낸다`() {
        repo.role = CrewRole.LEADER
        val vm = CrewManageViewModel(repo).also { it.bind(Routes.CrewManage(11)) }
        vm.onAsk(ManageAction.Appoint(members.members[1], 0))
        assertTrue(repo.calls.none { it.startsWith("appoint") })
        vm.onConfirm()
        assertTrue("appoint:2" in repo.calls)
        assertNull(vm.state.value.confirming)
    }
}
