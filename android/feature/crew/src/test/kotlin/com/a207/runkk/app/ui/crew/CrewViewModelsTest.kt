package com.a207.runkk.app.ui.crew

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.VoteAnswer
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CrewFindViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `공개 크루 목록과 추천을 함께 읽는다(FR-117·FR-118)`() {
        val vm = CrewFindViewModel(repo)
        val s = vm.state.value
        assertEquals(3, s.crews.size)
        assertEquals(listOf(crewA), s.recommendations)
        assertTrue("search:null:[]" in repo.calls)
    }

    @Test
    fun `태그를 고르면 그 태그로, 키워드는 검색할 때 다시 찾는다`() {
        val vm = CrewFindViewModel(repo)
        vm.onTagToggle("SOCIAL")
        vm.onTagToggle("SOLO")
        assertEquals(setOf("SOLO"), vm.state.value.tags, "같은 분류는 하나만")
        vm.onKeyword("  새벽 ")
        vm.load()
        assertEquals("search:새벽:[SOLO]", repo.calls.last { it.startsWith("search") })
    }

    @Test
    fun `자유 가입이면 바로 크루 홈으로, 승인제면 신청 안내(FR-120·FR-121)`() = runTest(main.dispatcher) {
        val vm = CrewFindViewModel(repo)
        vm.events.test {
            vm.onJoin(crewA)
            assertEquals(CrewEvent.Open(Routes.CrewHome(11, "새벽질주")), awaitItem())
        }
        vm.onJoin(crewC)
        assertEquals("가입 신청했어요. 크루장이 결정하면 알려 드려요", vm.state.value.message)
        assertEquals(JoinPolicy.APPROVAL, crewC.joinPolicy)
    }

    @Test
    fun `가입이 거부되면 사유를 보인다(정원·쿨타임)`() {
        repo.fail["apply"] = AppError.Rejected("CREW_COOLDOWN")
        val vm = CrewFindViewModel(repo)
        vm.onOpen(crewC)
        vm.onJoin(crewC)
        assertEquals("크루를 나온 뒤 24시간 동안은 가입하거나 만들 수 없어요", vm.state.value.message)
        assertFalse(vm.state.value.joining)
    }

    @Test
    fun `초대 코드는 6자리 영문·숫자 대문자로 받고 가입한다(FR-122)`() {
        val vm = CrewFindViewModel(repo)
        vm.onInviteOpen()
        vm.onInviteCode("ab-12cd99")
        assertEquals("AB12CD", vm.state.value.inviteCode)
        vm.onInviteSubmit()
        assertTrue("invite:AB12CD" in repo.calls)
        assertFalse(vm.state.value.inviteOpen)
    }
}

class CrewCreateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `이름 2~20자·태그 1~5개가 아니면 보내지 않는다`() {
        val vm = CrewCreateViewModel(repo)
        vm.onForm(CrewFormState(name = "a", tags = setOf("SOCIAL")))
        vm.onSubmit()
        vm.onForm(CrewFormState(name = "새벽질주"))
        vm.onSubmit()
        assertTrue(repo.calls.none { it.startsWith("create") })
    }

    @Test
    fun `만들면 그 크루 홈으로(FR-110)`() = runTest(main.dispatcher) {
        val vm = CrewCreateViewModel(repo)
        vm.onForm(CrewFormState(name = " 새벽질주 ", tags = setOf("SOCIAL", "BEGINNER_WELCOME")))
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewEvent.Open(Routes.CrewHome(77, "새벽질주")), awaitItem())
        }
    }
}

class CrewHomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm() = CrewHomeViewModel(repo).also { it.bind(Routes.CrewHome(11, "새벽질주")) }

    @Test
    fun `레이스 탭 - 이번 주 거리·기여도와 주간 랭킹, 기간을 바꾸면 다시 읽는다(FR-137·FR-140)`() {
        val vm = vm()
        assertEquals(race, vm.state.value.race)
        assertEquals(3, vm.state.value.ranking?.entries?.size)
        vm.onPeriod(CrewRanking.Period.MONTHLY)
        assertTrue("ranking:MONTHLY" in repo.calls)
        assertEquals(22, shareLabel(0.22))
        assertEquals(22, shareLabel(22.7))
    }

    @Test
    fun `탭은 처음 열 때 한 번만 읽는다`() {
        val vm = vm()
        vm.onTab(CrewTab.CHEER)
        vm.onTab(CrewTab.RACE)
        vm.onTab(CrewTab.CHEER)
        assertEquals(1, repo.calls.count { it == "posts:CHEER" })
        assertEquals(2, vm.state.value.cheers?.posts?.size)
    }

    @Test
    fun `응원글을 쓰면 비우고 목록을 다시 읽는다, 200자를 넘기지 않는다(FR-129)`() {
        val vm = vm()
        vm.onTab(CrewTab.CHEER)
        vm.onCheerDraft("가".repeat(250))
        assertEquals(200, vm.state.value.cheerDraft.length)
        vm.onCheerDraft("화이팅")
        vm.onCheerSubmit()
        assertTrue("cheer:화이팅" in repo.calls)
        assertEquals("", vm.state.value.cheerDraft)
        assertEquals(2, repo.calls.count { it == "posts:CHEER" })
    }

    @Test
    fun `내 글만 지운다(FR-130)`() {
        val vm = vm()
        vm.onTab(CrewTab.CHEER)
        vm.onDeletePost(cheers.posts[0])
        vm.onDeletePost(cheers.posts[1])
        assertEquals(listOf("deletePost:42"), repo.calls.filter { it.startsWith("deletePost") })
    }

    @Test
    fun `크루런 참여 응답을 남긴다(FR-145)`() {
        val vm = vm()
        vm.onTab(CrewTab.CREW_RUN)
        vm.onVote(crewRunPosts.posts[0], VoteAnswer.GOING)
        assertEquals(VoteAnswer.GOING, vm.state.value.votes[31])
        assertEquals(Distance(5_000), distanceHint(crewRunPosts.posts[0].summary))
        assertNull(distanceHint("토 07:00 · 7km"), "1·3·5·10km 밖은 쓰지 않는다")
    }

    @Test
    fun `확인 뒤 크루를 나가면 크루 탭으로(FR-125)`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.onLeaveAsk()
        assertTrue(vm.state.value.confirmLeave)
        vm.events.test {
            vm.onLeaveConfirm()
            assertEquals(CrewHomeEvent.Left, awaitItem())
        }
    }

    @Test
    fun `크루장이 크루원이 남은 채 나가려 하면 양도를 안내한다(FR-126)`() {
        repo.fail["leave"] = AppError.Rejected("CREW_LEADER_TRANSFER_REQUIRED")
        val vm = vm()
        vm.onLeaveConfirm()
        assertEquals("크루장 양도 필요. 크루원에게 크루장을 넘긴 뒤 탈퇴해 주세요", vm.state.value.message)
    }

    @Test
    fun `크루 내 소개는 40자까지 바꾼다(FR-144)`() {
        val vm = vm()
        vm.onIntroEdit()
        vm.onIntroDraft("가".repeat(50))
        assertEquals(40, vm.state.value.introDraft.length)
        vm.onIntroDraft("새벽 5시 고정")
        vm.onIntroSave()
        assertTrue("intro:새벽 5시 고정" in repo.calls)
    }
}

class CrewManageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm() = CrewManageViewModel(repo).also { it.bind(Routes.CrewManage(11, "새벽질주")) }

    @Test
    fun `가입 신청을 승인하면 안내하고 다시 읽는다(FR-123·FR-136)`() {
        val vm = vm()
        val request = vm.state.value.requests!!.requests.first()
        vm.onApprove(request)
        assertEquals("새내기님을 받았어요", vm.state.value.message)
        assertEquals(2, repo.calls.count { it == "pending" })
    }

    @Test
    fun `초대 코드를 새로 만든다(FR-114)`() {
        val vm = vm()
        vm.onReissue()
        assertEquals("K7P2QX", vm.state.value.inviteCode)
    }

    @Test
    fun `내보내기·크루장 넘기기는 확인 뒤에 보낸다(FR-115·FR-127)`() {
        val vm = vm()
        val member = members.members[1]
        vm.onAsk(ManageAction.Kick(member))
        assertTrue(repo.calls.none { it.startsWith("kick") })
        vm.onConfirm()
        assertTrue("kick:2" in repo.calls)
        vm.onAsk(ManageAction.Transfer(member))
        vm.onDismiss()
        assertTrue(repo.calls.none { it.startsWith("transfer") })
    }

    @Test
    fun `크루장이 아니면 서버 거부를 안내한다(FR-128)`() {
        repo.fail["reissue"] = AppError.Rejected("NOT_CREW_LEADER")
        val vm = vm()
        vm.onReissue()
        assertEquals("권한 없음. 크루장만 할 수 있어요", vm.state.value.message)
    }

    @Test
    fun `크루를 지우면 크루 탭으로(FR-116)`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.events.test {
            vm.onAsk(ManageAction.Delete)
            vm.onConfirm()
            assertEquals(CrewManageEvent.Deleted, awaitItem())
        }
    }

    @Test
    fun `정보 수정은 이름을 채워 두고 저장한다(FR-113)`() {
        val vm = vm()
        assertEquals("새벽질주", vm.state.value.form.name)
        vm.onForm(vm.state.value.form.copy(name = "새벽질주2", tags = setOf("DAWN")))
        vm.onSaveInfo()
        assertTrue("changeInfo:새벽질주2" in repo.calls)
        assertEquals("새벽질주2", vm.state.value.crewName)
    }
}

class CrewRunWriteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    @Test
    fun `지난 시각은 거부하고, 고른 거리·일시로 모집글을 올린다(FR-132)`() = runTest(main.dispatcher) {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onTitle("토요일 5K")
        vm.onDay(0)
        vm.onTime(LocalTime.of(7, 0))
        vm.onSubmit(11)
        assertEquals("지난 시각이에요. 모집 일시를 다시 골라 주세요", vm.state.value.error)
        vm.onDay(4)
        vm.onDistance(Distance(10_000))
        vm.events.test {
            vm.onSubmit(11)
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        val form = assertNotNull(repo.lastCrewRun)
        assertEquals(Distance(10_000), form.distance)
        assertEquals(java.time.Instant.parse("2026-10-23T22:00:00Z"), form.meetAt, "10/24(토) 07:00 KST")
    }
}
