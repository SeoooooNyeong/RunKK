package com.a207.runkk.app

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.LoginState
import com.a207.runkk.app.domain.notification.PushTokenRemote
import com.a207.runkk.app.domain.notification.PushTokenSource
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.DisposalRemote
import com.a207.runkk.app.domain.sync.SyncDisposalsUseCase
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * 앱 시작 작업 순서와 로그인 전환(13.2, D-43·D-53·D-54): 가짜 포트가 부른 순서를 한 목록에 기록해 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupTest {
    private val events = mutableListOf<String>()
    private val login = MutableStateFlow(true)
    private val settings = RecordingSettings(events)
    private val pushRemote = RecordingPushRemote(events)

    private fun TestScope.startup(): AppStartup {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val pending = NoPendingRuns()
        val disposals = SyncDisposalsUseCase(pending, RecordingDisposalRemote(events))
        val loginState =
            object : LoginState {
                override val loggedIn: StateFlow<Boolean> = login
            }
        val push = PushTokenSync(pushRemote, FixedPushToken("fcm-token"), loginState, scope)
        return AppStartup(disposals, settings, push, loginState, scope)
    }

    @Test
    fun `시작하면 파기 지시, 설정 갱신, 푸시 토큰 등록 순서로 한다`() = runTest {
        startup().start()
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register"), events)
    }

    @Test
    fun `로그아웃 상태로 시작하면 푸시 등록은 건너뛴다`() = runTest {
        login.value = false

        startup().start()
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh"), events)
    }

    @Test
    fun `로그인 상태가 참이 되면 세 작업을 다시 한다`() = runTest {
        login.value = false
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = true
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register"), events)
    }

    @Test
    fun `로그인 상태가 거짓이 되면 설정 사본을 지운다`() = runTest {
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = false
        advanceUntilIdle()

        assertEquals(listOf("settings.clear"), events)
    }

    @Test
    fun `로그아웃 뒤 다시 로그인하면 지우고 나서 다시 받는다`() = runTest {
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = false
        advanceUntilIdle()
        login.value = true
        advanceUntilIdle()

        assertEquals(
            listOf("settings.clear", "disposals", "settings.refresh", "push.register"),
            events
        )
    }

    @Test
    fun `시작 작업이 실패해도 뒤 작업과 로그인 전환 구독은 이어진다`() = runTest {
        settings.refreshResult = Outcome.Failure(AppError.Network)
        pushRemote.result = Outcome.Failure(AppError.Network)
        startup().start()
        advanceUntilIdle()

        login.value = false
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register", "settings.clear"), events)
    }
}

private class RecordingDisposalRemote(private val events: MutableList<String>) : DisposalRemote {
    override suspend fun pending(): Outcome<List<DisposalOrder>> {
        events += "disposals"
        return Outcome.Success(emptyList())
    }

    override suspend fun confirm(orderId: Long): Outcome<Unit> = Outcome.Success(Unit)
}

private class RecordingPushRemote(private val events: MutableList<String>) : PushTokenRemote {
    var result: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun register(token: String): Outcome<Unit> {
        events += "push.register"
        return result
    }
}

private class FixedPushToken(private val token: String?) : PushTokenSource {
    override suspend fun currentToken(): String? = token
}

private class RecordingSettings(private val events: MutableList<String>) : SettingsRepository {
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun refresh(): Outcome<Unit> {
        events += "settings.refresh"
        return refreshResult
    }

    override fun locationConsented(): Boolean = false

    override fun heartRateConsented(): Boolean = false

    override fun watchLinked(): Boolean = false

    override fun courseMapShared(): Boolean = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings(): GuidanceSettings = GuidanceSettings.DEFAULT

    override fun changes(): Flow<SettingsSnapshot> = emptyFlow()

    override suspend fun clear(): Outcome<Unit> {
        events += "settings.clear"
        return Outcome.Success(Unit)
    }
}

/** 파기 지시가 없는 시험이라 보관함은 건드리지 않는다. */
private class NoPendingRuns : PendingRunRepository {
    override suspend fun save(run: CompletedRun): Outcome<Unit> = unused()

    override suspend fun pending(): Outcome<List<CompletedRun>> = unused()

    override fun observePending(): Flow<List<CompletedRun>> = emptyFlow()

    override suspend fun remove(recordId: String): Outcome<Unit> = unused()

    override suspend fun purgeRoutes(): Outcome<Unit> = unused()

    override suspend fun purgeHeartRates(): Outcome<Unit> = unused()

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = unused()

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> = unused()

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = unused()

    override suspend fun removeLateHeartRates(recordId: String): Outcome<Unit> = unused()

    override suspend fun clear(): Outcome<Unit> = unused()

    private fun <T> unused(): T = throw UnsupportedOperationException("이 시험은 보관함을 쓰지 않는다")
}
