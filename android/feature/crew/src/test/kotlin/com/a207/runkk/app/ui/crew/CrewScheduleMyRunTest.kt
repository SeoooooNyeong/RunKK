package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.MyRunState
import com.a207.runkk.app.domain.crew.PostList
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 크루런 탭 진행 중 카드의 단추를 내 참여 상태(myRun)로 고른다(피그마 F-11e·f·g). */
@RunWith(RobolectricTestRunner::class)
class CrewScheduleMyRunTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun runningPost(myRun: MyRunState) = crewRunPosts.posts[0].copy(
        crewRun = crewRunVoting.copy(status = CrewRunStatus.RUNNING, finisherCount = 3, myRun = myRun)
    )

    private fun show(myRun: MyRunState, heroResults: Map<Long, CrewRunResult> = emptyMap()) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.SCHEDULE,
                        crewRuns = PostList(listOf(runningPost(myRun))),
                        lobbyEntries = mapOf(31L to LobbyAvailability.Closed),
                        scheduleNow = crewRunVoting.scheduledAt.plusSeconds(723),
                        heroResults = heroResults
                    ),
                    CrewHomeActions(
                        onJoinRun = { calls += "join:${it.postId}" },
                        onResumeRun = { calls += "resume:${it.postId}" },
                        onResult = { calls += "result:${it.postId}" }
                    )
                )
            }
        }
    }

    @Test
    fun `끝낸 사람은 내 거리와 완주했어요, 결과 보기를 보고 누르면 결과로 간다`() {
        show(MyRunState.ENDED, mapOf(31L to crewRunResult))
        compose.onNodeWithText("4.87km").assertExists()
        compose.onNodeWithText("완주했어요").assertExists()
        compose.onNodeWithText("중간 참여하기").assertDoesNotExist()
        compose.onNodeWithText("결과 보기").performClick()
        assertEquals(listOf("result:31"), calls)
    }

    @Test
    fun `측정 중인 사람은 지난 시간과 달리는 중, 이어 달리기를 보고 누르면 진행 화면으로 간다`() {
        show(MyRunState.RUNNING)
        compose.onNodeWithText("3명 달리는 중").assertExists()
        compose.onNodeWithText("12:03").assertExists()
        compose.onNodeWithText("째 달리는 중").assertExists()
        compose.onNodeWithText("이어 달리기").performClick()
        assertEquals(listOf("resume:31"), calls)
    }

    @Test
    fun `참여 전인 사람은 지난 시간과 진행 중, 중간 참여하기를 본다`() {
        show(MyRunState.NONE)
        compose.onNodeWithText("째 진행 중").assertExists()
        compose.onNodeWithText("중간 참여하기").performClick()
        assertEquals(listOf("join:31"), calls)
    }

    @Test
    fun `이어 달리기는 같은 크루런 대기실(진행 화면) 경로로 간다`() {
        val routes = mutableListOf<Any>()
        val actions = CrewHomeActions().withNavigation(11) { routes += it }
        actions.onResumeRun(runningPost(MyRunState.RUNNING))
        actions.onResult(runningPost(MyRunState.ENDED))
        assertEquals(listOf<Any>(Routes.CrewRunLobby(31), Routes.CrewRunResult(31)), routes)
    }
}

class CrewScheduleMyRunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    private fun vm() = CrewHomeViewModel(repo, ServerClock(clock), FakeCelebrationSharer(), { _, _ ->
        Outcome.Success(Unit)
    }).also { it.bind(Routes.CrewHome(11)) }

    @Test
    fun `진행 중 강조 카드면 결과를 한 번 읽어 내 거리·출발 시각을 담고 진행 중이 아니면 읽지 않는다`() = main.runVmTest { vms ->
        clock.now = crewRunVoting.scheduledAt.plusSeconds(600)
        repo.crewRunList = PostList(
            listOf(
                crewRunPosts.posts[0].copy(
                    crewRun = crewRunVoting.copy(status = CrewRunStatus.RUNNING, myRun = MyRunState.ENDED)
                )
            )
        )
        val vm = vms.track(vm())
        vm.onTab(CrewTab.SCHEDULE)
        runCurrent()
        assertEquals(crewRunResult, vm.state.value.heroResults[31])
        assertEquals(1, repo.calls.count { it == "result:31" })

        repo.crewRunList = crewRunPosts
        val idle = vms.track(vm())
        idle.onTab(CrewTab.SCHEDULE)
        runCurrent()
        assertNull(idle.state.value.heroResults[31])
        assertEquals(1, repo.calls.count { it == "result:31" }, "투표 중 카드는 결과를 읽지 않는다")
    }
}
