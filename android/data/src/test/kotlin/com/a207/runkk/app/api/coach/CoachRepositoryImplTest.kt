package com.a207.runkk.app.api.coach

import com.a207.runkk.app.api.core.ApiCoreBindings
import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.coach.CoachEvent
import com.a207.runkk.app.domain.coach.CoachFailure
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import retrofit2.create

class CoachRepositoryImplTest {
    private val http = ApiTestServer()
    private val tokens = TestTokens()
    private val client = ApiCoreBindings.authenticatedClient(
        OkHttpClient(),
        tokens,
        http.server.url("/api/v1/auth/refresh"),
        http.json
    )
    private val repository = CoachRepositoryImpl(
        http.retrofit.newBuilder().client(client).build().create(),
        http.json,
        tokens
    )

    @AfterTest
    fun close() = http.close()

    @Test
    fun `목록으로 서버 동의를 확인하고 대화 ID와 시각을 읽는다`() = runTest {
        http.reply("""{"code":"CONSENT_REQUIRED","message":"동의해 주세요"}""", 403)
        assertEquals(Outcome.Failure(AppError.Rejected("CONSENT_REQUIRED")), repository.conversations())
        assertEquals("/api/v1/coach/conversations", http.take().url.encodedPath)
        http.reply("""[{"conversationId":"conv-1","firstQuestion":"회복","lastAskedAt":"2026-11-09T09:12:00"}]""")
        val conversation = repository.conversations().value().single()
        assertEquals("conv-1", conversation.conversationId)
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 12), conversation.lastAskedAt)
    }

    @Test
    fun `기존 대화 요청과 분할된 SSE 응답을 계약대로 연결한다`() = runTest {
        verifyConsent()
        replyStream(
            STARTED + "event:delta\r\ndata:{\"text\":\r\ndata:\"천천히 달려요\"}\r\n\r\n" +
                "event:done\ndata:{}\n\n"
        )
        assertEquals(
            listOf(CoachEvent.Started("conv-1"), CoachEvent.Delta("천천히 달려요"), CoachEvent.Done),
            repository.ask("conv-1", "회복할까요?").toList()
        )
        val request = http.take()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/coach/questions", request.url.encodedPath)
        assertEquals("text/event-stream", request.headers["Accept"])
        assertEquals(
            http.json.parseToJsonElement("""{"conversationId":"conv-1","text":"회복할까요?"}"""),
            http.json.parseToJsonElement(request.body!!.utf8())
        )
    }

    @Test
    fun `전송 거부와 끊긴 응답은 완료 사건으로 바뀌지 않는다`() = runTest {
        verifyConsent()
        http.reply("""{"code":"COACH_QUOTA_EXCEEDED"}""", 429)
        assertEquals(CoachEvent.Failed(CoachFailure.QUOTA), repository.ask(null, "질문").first())
        replyStream(STARTED)
        assertEquals(
            listOf(CoachEvent.Started("conv-1"), CoachEvent.Failed(CoachFailure.RETRY)),
            repository.ask(null, "질문").toList()
        )
    }

    @Test
    fun `첫 사건만 받은 뒤 취소하면 지연 중인 본문을 기다리지 않는다`() = runBlocking {
        verifyConsent()
        http.server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body(STARTED + "event:done\ndata:{}\n\n")
                .throttleBody(STARTED.toByteArray().size.toLong(), 30, TimeUnit.SECONDS)
                .build()
        )
        val first = withTimeout(5_000) { repository.ask(null, "질문").first() }
        assertEquals(CoachEvent.Started("conv-1"), first)
    }

    @Test
    fun `계정이 바뀌면 이전 동의로 보내거나 새 토큰으로 재전송하지 않는다`() = runTest {
        verifyConsent()
        http.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                tokens.generation = 2
                return MockResponse.Builder().code(401).build()
            }
        }
        assertEquals(emptyList(), repository.ask(null, "이전 계정 질문").toList())
        assertEquals("Bearer first", http.take().headers["Authorization"])
        assertEquals(
            listOf(CoachEvent.Failed(CoachFailure.RETRY)),
            repository.ask(null, "계정 변경 뒤 질문").toList()
        )
        assertEquals(2, http.server.requestCount)
    }

    private suspend fun verifyConsent() {
        http.reply("[]")
        assertEquals(emptyList(), repository.conversations().value())
        http.take()
    }

    private fun replyStream(body: String) {
        http.server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(body).build())
    }

    private companion object {
        const val STARTED = "event:started\ndata:{\"conversationId\":\"conv-1\"}\n\n"
    }

    private class TestTokens : TokenProvider {
        @Volatile
        var generation = 1L

        override fun sessionGeneration() = generation
        override fun accessToken() = if (generation == 1L) "first" else "second"
        override fun refreshToken(): String? = null
        override fun update(access: String, refresh: String) = Unit
        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}
