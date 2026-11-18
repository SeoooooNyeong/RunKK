@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.PlacementStatus
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 배치고사 완료(C-16 v2): 확정된 Runimal 유형이 있으면 요약 카드 아래 Runimal 카드를 보인다. */
class PlacementRunimalViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val accounts = FakeAccounts()

    @Test
    fun `확정 Runimal 유형이 있으면 유형과 한 줄 설명을 담고 없으면 비워 둔다`() = main.runVmTest { vms ->
        accounts.placementResult =
            Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD").copy(runimalType = "COMMUTER_TURTLE"))
        val asked = mutableListOf<String>()
        val vm = vms.track(
            PlacementCompleteViewModel(accounts) { type ->
                asked += type
                "같은 길을 오래"
            }
        )
        runCurrent()
        assertEquals("COMMUTER_TURTLE", vm.state.value.runimalType)
        assertEquals("같은 길을 오래", vm.state.value.runimalDescription)
        assertEquals(listOf("COMMUTER_TURTLE"), asked)

        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val none = vms.track(PlacementCompleteViewModel(accounts) { error("부르지 않는다") })
        runCurrent()
        assertNull(none.state.value.runimalType)
    }
}

@RunWith(RobolectricTestRunner::class)
class PlacementRunimalScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(runimalType: String?) = compose.setContent {
        RunkkTheme(reduceMotion = true) {
            PlacementCompleteScreen(
                PlacementCompleteUiState(
                    isLoading = false,
                    tier = "GOLD",
                    wins = 2,
                    losses = 1,
                    runimalType = runimalType,
                    runimalDescription = runimalType?.let { "같은 길을 오래" }
                ),
                PlacementCompleteActions()
            )
        }
    }

    @Test
    fun `확정 Runimal이 있으면 요약 카드 아래 Runimal 확정 카드를 보인다`() {
        show("COMMUTER_TURTLE")
        compose.onNodeWithText("골드 티어가 됐어요").assertExists()
        compose.onNodeWithText("Runimal 확정").performScrollTo().assertExists()
        compose.onNodeWithText("출근도장 거북이").assertExists()
        compose.onNodeWithText("같은 길을 오래").assertExists()
    }

    @Test
    fun `확정 Runimal이 없으면 Runimal 카드가 없다`() {
        show(null)
        compose.onNodeWithText("Runimal 확정").assertDoesNotExist()
    }
}
