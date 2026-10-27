package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** 게스트·연동·로그아웃(FR-197~FR-199, FR-002, D-89, D-159): 시험 문서 2.2.16 TC-6431~TC-6442 중 domain 대상. */
class GuestAccountUseCaseTest {
    private val log = CallLog()
    private val account = FakeAccountRepository(log)
    private val session = FakeSessionRepository(log)
    private val settings = FakeSettingsRepository(log = log)
    private val pending = FakePendingRunRepository(log)
    private val kakao = ProviderToken(ProviderType.KAKAO, "tok")
    private val consents =
        mapOf("TERMS" to true, "PRIVACY" to true, "LOCATION" to true, "MARKETING" to false, "LLM_TRANSFER" to false)

    private suspend fun startAsGuest(): Outcome<Unit> = StartAsGuestUseCase(account, session, settings)(consents)

    private suspend fun link(): Outcome<Unit> = LinkSocialAccountUseCase(account, session, settings)(kakao)

    private suspend fun logout(): Outcome<Unit> = LogoutUseCase(account, session, pending, settings)()

    @Test
    fun `TC-6431 게스트 시작은 자격 증명을 먼저 저장하고 토큰을 저장한 뒤 설정을 갱신한다`() = runTest {
        assertEquals(Outcome.Success(Unit), startAsGuest())
        assertEquals(
            listOf(
                "account.startAsGuest",
                "session.saveGuestCredential:g1.42.credential",
                "session.save",
                "settings.refresh"
            ),
            log.calls
        )
        assertEquals(true, session.hasGuestCredential())
        assertEquals(SessionTokens("A1", "R1"), session.saved)
    }

    @Test
    fun `TC-6432 서버가 거부하면 아무것도 저장하지 않는다`() = runTest {
        val errors = listOf(AppError.Rejected("UNDER_14"), AppError.Rejected("CONSENT_MISSING"), AppError.Server(429))
        for (error in errors) {
            log.calls.clear()
            account.failures["startAsGuest"] = error
            assertEquals(Outcome.Failure(error), startAsGuest())
            assertEquals(listOf("account.startAsGuest"), log.calls)
        }
    }

    @Test
    fun `TC-6433 자격 증명 저장이 실패하면 토큰을 저장하지 않고 아무것도 남기지 않는다`() = runTest {
        session.credentialFailure = AppError.Storage
        assertEquals(Outcome.Failure(AppError.Storage), startAsGuest())
        assertEquals(0, log.calls.count { it == "session.save" || it == "settings.refresh" })
        assertEquals(false, session.hasGuestCredential())
        assertEquals(null, session.saved)
    }

    @Test
    fun `TC-6434 토큰 저장이 실패하면 자격 증명도 지운다`() = runTest {
        session.saveFailure = AppError.Storage
        assertEquals(Outcome.Failure(AppError.Storage), startAsGuest())
        assertEquals(0, log.calls.count { it == "settings.refresh" })
        assertEquals(false, session.hasGuestCredential())
        assertEquals(null, session.saved)
    }

    @Test
    fun `TC-6435 설정 사본 갱신 실패는 시작을 막지 않는다`() = runTest {
        settings.refreshResult = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Success(Unit), startAsGuest())
    }

    @Test
    fun `TC-6436 연동 뒤 자격 증명을 지우고 설정을 갱신하며 토큰은 그대로다`() = runTest {
        session.credential = "g1.42.credential"
        assertEquals(Outcome.Success(Unit), link())
        assertEquals(
            listOf("account.linkSocialAccount", "session.clearGuestCredential", "settings.refresh"),
            log.calls
        )
        assertEquals(false, session.hasGuestCredential())
    }

    @Test
    fun `TC-6437 다른 회원과 연동된 계정·네트워크 실패면 자격 증명을 그대로 둔다`() = runTest {
        val errors = listOf(AppError.Rejected("ALREADY_MEMBER"), AppError.Network)
        for (error in errors) {
            log.calls.clear()
            session.credential = "g1.42.credential"
            account.failures["linkSocialAccount"] = error
            assertEquals(Outcome.Failure(error), link())
            assertEquals(true, session.hasGuestCredential())
            assertEquals(listOf("account.linkSocialAccount"), log.calls)
        }
    }

    /** 총괄 결정(A1 설계 갱신 요청 1): ALREADY_LINKED는 이미 연동한 회원이므로 기기 자격 증명도 지운다(시험서 정정 요청). */
    @Test
    fun `TC-6437 이미 연동한 회원(ALREADY_LINKED)이면 거부를 돌려주되 자격 증명을 지우고 설정을 갱신한다`() = runTest {
        session.credential = "g1.42.credential"
        account.failures["linkSocialAccount"] = AppError.Rejected("ALREADY_LINKED")
        assertEquals(Outcome.Failure(AppError.Rejected("ALREADY_LINKED")), link())
        assertEquals(false, session.hasGuestCredential())
        assertEquals(
            listOf("account.linkSocialAccount", "session.clearGuestCredential", "settings.refresh"),
            log.calls
        )
    }

    @Test
    fun `TC-6438 연동 뒤 설정 갱신 실패는 연동을 막지 않는다`() = runTest {
        session.credential = "g1.42.credential"
        settings.refreshResult = Outcome.Failure(AppError.Network)
        assertEquals(Outcome.Success(Unit), link())
        assertEquals(false, session.hasGuestCredential())
    }

    @Test
    fun `TC-6439 게스트 로그아웃은 서버 로그아웃 뒤 세션·미전송 기록·설정 사본을 지운다`() = runTest {
        session.credential = "g1.42.credential"
        settings.snapshot = settings.snapshot.copy(isGuest = true)
        assertEquals(Outcome.Success(Unit), logout())
        assertEquals(listOf("account.logout", "session.clear", "pending.clear", "settings.clear"), log.calls)
        assertEquals(false, session.hasGuestCredential())
        assertEquals(false, session.loggedIn.value)
    }

    @Test
    fun `TC-6440 연동 회원 로그아웃은 서버 실패와 무관하게 미전송 기록을 남긴다`() = runTest {
        account.failures["logout"] = AppError.Network
        logout()
        assertEquals(listOf("account.logout", "session.clear", "settings.clear"), log.calls)
    }

    @Test
    fun `TC-6441 게스트 로그아웃은 서버 로그아웃이 실패해도 기기 자료를 지운다`() = runTest {
        session.credential = "g1.42.credential"
        settings.snapshot = settings.snapshot.copy(isGuest = true)
        account.failures["logout"] = AppError.Network
        logout()
        assertEquals(listOf("account.logout", "session.clear", "pending.clear", "settings.clear"), log.calls)
    }

    @Test
    fun `TC-6442 연동 뒤 로그아웃은 연동 회원 갈래로 미전송 기록을 남긴다`() = runTest {
        session.credential = "g1.42.credential"
        link()
        log.calls.clear()
        logout()
        assertEquals(0, log.calls.count { it == "pending.clear" })
    }

    /** 총괄 결정(A1 설계 갱신 요청 1): 로그아웃 갈래는 자격 증명 유무가 아니라 설정 사본의 계정 유형으로 정한다. */
    @Test
    fun `자격 증명이 남아 있어도 계정 유형이 연동 회원이면 미전송 기록을 지우지 않는다`() = runTest {
        session.credential = "g1.42.credential"
        settings.snapshot = settings.snapshot.copy(isGuest = false)
        logout()
        assertEquals(listOf("account.logout", "session.clear", "settings.clear"), log.calls)
    }

    /** 리뷰 반영(A2 결정): 연동 뒤 설정 사본 갱신이 실패해 사본이 아직 GUEST여도 자격 증명을 지웠으므로 미전송 기록을 남긴다. */
    @Test
    fun `연동 뒤 설정 갱신이 실패해 사본이 게스트로 남아도 미전송 기록을 지우지 않는다`() = runTest {
        session.credential = "g1.42.credential"
        settings.snapshot = settings.snapshot.copy(isGuest = true)
        settings.refreshResult = Outcome.Failure(AppError.Network)
        link()
        log.calls.clear()
        logout()
        assertEquals(listOf("account.logout", "session.clear", "settings.clear"), log.calls)
    }
}
