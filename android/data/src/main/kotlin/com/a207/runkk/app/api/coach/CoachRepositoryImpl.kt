package com.a207.runkk.app.api.coach

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.BoundSession
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.coach.CoachConversation
import com.a207.runkk.app.domain.coach.CoachEvent
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.domain.coach.CoachMessage
import com.a207.runkk.app.domain.coach.CoachRepository
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.BufferedSource

internal class CoachRepositoryImpl @Inject constructor(
    private val api: CoachApi,
    private val json: Json,
    private val tokens: TokenProvider
) : CoachRepository {
    @Volatile
    private var verifiedGeneration: Long? = null

    override suspend fun conversations(): Outcome<List<CoachConversation>> {
        verifiedGeneration = null
        val session = BoundSession.capture(tokens) ?: return Outcome.Failure(AppError.Unauthorized)
        return ApiErrorMapper.call {
            val conversations = api.conversations(session).map {
                CoachConversation(it.conversationId, it.firstQuestion, LocalDateTime.parse(it.lastAskedAt))
            }
            if (!session.isCurrent(tokens)) throw IOException("Coach session changed")
            verifiedGeneration = session.generation
            conversations
        }
    }

    override suspend fun messages(conversationId: String): Outcome<List<CoachMessage>> {
        val session = BoundSession.capture(tokens) ?: return Outcome.Failure(AppError.Unauthorized)
        return ApiErrorMapper.call {
            val messages = api.messages(session, conversationId).map {
                val role = when (it.role) {
                    "QUESTION" -> CoachMessage.Role.QUESTION
                    "ANSWER" -> CoachMessage.Role.ANSWER
                    else -> throw SerializationException("Invalid coach message role")
                }
                CoachMessage(role, it.text, LocalDateTime.parse(it.at))
            }
            // 읽는 사이 계정이 바뀌면 이전 계정의 문답을 보이지 않는다.
            if (!session.isCurrent(tokens)) throw IOException("Coach session changed")
            messages
        }
    }

    override fun ask(conversationId: String?, text: String): Flow<CoachEvent> = callbackFlow {
        val session = BoundSession.capture(tokens)
        if (session == null || session.generation != verifiedGeneration) {
            send(CoachEvent.Failed(CoachFailure.RETRY))
            close()
            return@callbackFlow
        }
        val call = api.ask(session, CoachApi.Question(conversationId, text))
        launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val body = response.errorBody()?.use { it.string() }
                    val error = ApiErrorMapper.fromHttp(response.code(), body)
                    if (session.isCurrent(tokens)) {
                        send(CoachEvent.Failed(failureOf((error as? AppError.Rejected)?.code)))
                    }
                } else {
                    val body = response.body() ?: throw IOException("Missing coach stream")
                    body.use {
                        val media = it.contentType()
                        if (media?.type != "text" || media.subtype != "event-stream") {
                            throw IOException("Invalid coach stream")
                        }
                        readEvents(it.source()) { event ->
                            if (!session.isCurrent(tokens)) throw CancellationException("Coach session changed")
                            send(event)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (session.isCurrent(tokens)) send(CoachEvent.Failed(CoachFailure.RETRY))
            } finally {
                close()
            }
        }
        // Retrofit의 응답이 열린 뒤에도 취소가 블로킹 본문 읽기까지 전달되어야 한다.
        awaitClose { call.cancel() }
    }

    private suspend fun readEvents(source: BufferedSource, emit: suspend (CoachEvent) -> Unit) {
        var name = ""
        var started = false
        val data = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8LineStrict(MAX_FRAME_CHARS.toLong())
            when {
                line.isEmpty() -> {
                    val event = when (name) {
                        "started" -> {
                            if (started) throw IOException("Duplicate coach start")
                            started = true
                            CoachEvent.Started(field(data, "conversationId"))
                        }

                        "delta" -> {
                            if (!started) throw IOException("Missing coach start")
                            CoachEvent.Delta(field(data, "text"))
                        }

                        "done" -> {
                            if (!started) throw IOException("Missing coach start")
                            json.parseToJsonElement(data.toString()).jsonObject
                            CoachEvent.Done
                        }

                        "failed" -> CoachEvent.Failed(failureOf(field(data, "reason")))

                        else -> null
                    }
                    if (event != null) emit(event)
                    if (event == CoachEvent.Done || event is CoachEvent.Failed) return
                    name = ""
                    data.clear()
                }

                line.startsWith("event:") -> name = line.substringAfter(':').trim()

                line.startsWith("data:") -> {
                    if (data.length + line.length > MAX_FRAME_CHARS) throw IOException("Coach event too large")
                    data.appendLine(line.substringAfter(':').removePrefix(" "))
                }
            }
        }
        throw IOException("Coach stream ended without completion")
    }

    private fun field(data: StringBuilder, key: String): String {
        val value = json.parseToJsonElement(data.toString()).jsonObject[key] as? JsonPrimitive
        return value?.takeIf { it.isString }?.content ?: throw IOException("Invalid coach event field")
    }

    private fun failureOf(code: String?): CoachFailure = when (code) {
        "CONSENT_REQUIRED" -> CoachFailure.CONSENT_REQUIRED
        "FEATURE_CLOSED" -> CoachFailure.FEATURE_CLOSED
        "CONVERSATION_EXPIRED" -> CoachFailure.EXPIRED
        "QUESTION_TOO_LONG" -> CoachFailure.TOO_LONG
        "COACH_QUOTA_EXCEEDED" -> CoachFailure.QUOTA
        else -> CoachFailure.RETRY
    }

    private companion object {
        const val MAX_FRAME_CHARS = 64 * 1024
    }
}
