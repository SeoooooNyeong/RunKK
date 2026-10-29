package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeGuideRemote(private val log: CallLog) : GuideRemote {
    var seen: Outcome<Boolean> = Outcome.Success(false)
    var markResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun seen(): Outcome<Boolean> {
        log.add("remote.seen")
        return seen
    }

    override suspend fun markSeen(): Outcome<Unit> {
        log.add("remote.markSeen")
        return markResult
    }
}

/** 첫 사용 가이드 띄움 판정과 끝냄 저장(D-277, FR-231). 시험 설계 10-06 TC-9714·TC-9715, 기초 경로 TC-9751~TC-9754. */
class FirstRunGuideTest {
    private val log = CallLog()
    private val settings = FakeSettingsRepository(log = log)
    private val remote = FakeGuideRemote(log)
    private val guide = FirstRunGuide(settings, remote)

    @Test
    fun `TC-9751 사본이 끝냄이면 서버를 묻지 않고 띄우지 않는다`() = runTest {
        settings.guide = GuideCopy.SEEN
        assertFalse(guide.shouldShow(loggedIn = true))
        settings.guide = GuideCopy.SEEN_UNSENT
        assertFalse(guide.shouldShow(loggedIn = true))
        assertEquals(emptyList(), log.calls)
    }

    @Test
    fun `TC-9752 로그인하지 않았으면 서버를 묻지 않고 띄우지 않는다`() = runTest {
        assertFalse(guide.shouldShow(loggedIn = false))
        assertEquals(emptyList(), log.calls)
    }

    @Test
    fun `TC-9753 서버가 이미 봤다고 하면 사본을 맞추고 띄우지 않는다`() = runTest {
        remote.seen = Outcome.Success(true)
        assertFalse(guide.shouldShow(loggedIn = true))
        assertEquals(GuideCopy.SEEN, settings.guide)
        assertEquals(listOf("remote.seen", "settings.saveGuideCopy:SEEN"), log.calls)
    }

    @Test
    fun `TC-9754 서버도 아직이면 띄운다`() = runTest {
        remote.seen = Outcome.Success(false)
        assertTrue(guide.shouldShow(loggedIn = true))
        assertEquals(GuideCopy.NONE, settings.guide)
    }

    @Test
    fun `TC-9714 서버를 읽지 못하면 기기 사본만 보고 띄운다`() = runTest {
        remote.seen = Outcome.Failure(AppError.Network)
        assertTrue(guide.shouldShow(loggedIn = true))
    }

    @Test
    fun `TC-9715 끝내면 사본에 먼저 쓰고 서버에 알린다`() = runTest {
        assertEquals(Outcome.Success(Unit), guide.complete())
        assertEquals(
            listOf("settings.saveGuideCopy:SEEN_UNSENT", "remote.markSeen", "settings.saveGuideCopy:SEEN"),
            log.calls
        )
        assertFalse(guide.shouldShow(loggedIn = true))
    }

    @Test
    fun `TC-9715 서버 알림이 실패해도 다시 뜨지 않고 다음 시작 때 다시 보낸다`() = runTest {
        remote.markResult = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Failure(AppError.Network), guide.complete())
        assertEquals(GuideCopy.SEEN_UNSENT, settings.guide)
        assertFalse(guide.shouldShow(loggedIn = true))

        remote.markResult = Outcome.Success(Unit)
        log.calls.clear()
        assertEquals(Outcome.Success(Unit), guide.resendCompletion())
        assertEquals(listOf("remote.markSeen", "settings.saveGuideCopy:SEEN"), log.calls)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `다시 보낼 끝냄이 없으면 서버를 부르지 않는다`() = runTest {
        guide.resendCompletion()
        settings.guide = GuideCopy.SEEN
        guide.resendCompletion()
        assertEquals(emptyList(), log.calls)
    }
}
