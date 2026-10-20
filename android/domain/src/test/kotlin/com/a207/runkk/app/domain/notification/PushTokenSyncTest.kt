package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class FakePushTokenRemote : PushTokenRemote {
    val registered = mutableListOf<String>()
    var result: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun register(token: String): Outcome<Unit> {
        registered += token
        return result
    }
}

private class FakePushTokenSource(var token: String?) : PushTokenSource {
    override suspend fun currentToken(): String? = token
}

private class FakeLoginState(loggedIn: Boolean) : LoginState {
    val state = MutableStateFlow(loggedIn)
    override val loggedIn: StateFlow<Boolean> = state
}

/** PushTokenSync(D-43, design-review.md E19). */
@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenSyncTest {
    private val remote = FakePushTokenRemote()

    @Test
    fun `토큰 변경 - 로그아웃 상태면 등록하지 않는다`() = runTest {
        val sync = PushTokenSync(remote, FakePushTokenSource("t"), FakeLoginState(false), backgroundScope)
        sync.onTokenChanged("new-token")
        runCurrent()
        assertEquals(emptyList(), remote.registered)
    }

    @Test
    fun `토큰 변경 - 로그인 상태면 등록한다`() = runTest {
        val sync = PushTokenSync(remote, FakePushTokenSource("t"), FakeLoginState(true), backgroundScope)
        sync.onTokenChanged("new-token")
        runCurrent()
        assertEquals(listOf("new-token"), remote.registered)
    }

    @Test
    fun `syncCurrent - 로그인 상태면 지금 토큰을 보낸다`() = runTest {
        val sync = PushTokenSync(remote, FakePushTokenSource("current"), FakeLoginState(true), backgroundScope)
        sync.syncCurrent()
        assertEquals(listOf("current"), remote.registered)
    }

    @Test
    fun `syncCurrent - 로그아웃 상태이거나 토큰이 없으면 보내지 않는다`() = runTest {
        PushTokenSync(remote, FakePushTokenSource("current"), FakeLoginState(false), backgroundScope).syncCurrent()
        PushTokenSync(remote, FakePushTokenSource(null), FakeLoginState(true), backgroundScope).syncCurrent()
        assertEquals(emptyList(), remote.registered)
    }

    @Test
    fun `syncCurrent - 등록 실패는 던지지 않는다(다음 시작·로그인 때 다시 보낸다)`() = runTest {
        remote.result = Outcome.Failure(AppError.Network)
        PushTokenSync(remote, FakePushTokenSource("current"), FakeLoginState(true), backgroundScope).syncCurrent()
        assertEquals(listOf("current"), remote.registered)
    }
}
