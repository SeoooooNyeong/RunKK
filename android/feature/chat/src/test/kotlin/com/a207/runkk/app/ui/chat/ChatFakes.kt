package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatPage
import com.a207.runkk.app.domain.chat.ChatReactions
import com.a207.runkk.app.domain.chat.ChatRepository
import com.a207.runkk.app.domain.chat.OpenChatRoomRegistry
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportStatus
import com.a207.runkk.app.domain.report.ReportTarget
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

val T0: Instant = Instant.parse("2026-10-24T22:00:00Z")

/** Dispatchers.Main을 시험 디스패처로 바꾼다(viewModelScope). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun chatMessage(
    id: Long?,
    content: String = "m$id",
    isMine: Boolean = false,
    canDelete: Boolean = isMine,
    reactions: Map<ReactionKind, Int> = emptyMap(),
    myReactions: Set<ReactionKind> = emptySet(),
    sendState: ChatMessage.SendState = ChatMessage.SendState.SENT,
    clientMessageId: String? = null,
    authorName: String = if (isMine) "나" else "바람처럼",
    sentAt: Instant = T0
) = ChatMessage(
    messageId = id,
    authorId = if (isMine) 1 else 2,
    authorName = authorName,
    content = content,
    sentAt = sentAt,
    reactions = reactions,
    myReactions = myReactions,
    isMine = isMine,
    canDelete = canDelete,
    clientMessageId = clientMessageId,
    sendState = sendState
)

class FakeChatRepository : ChatRepository {
    val historyCalls = mutableListOf<Triple<Long?, Long?, Int>>()
    val historyResults = ArrayDeque<Outcome<ChatPage>>()
    val posts = mutableListOf<Pair<String, String>>()
    val postResults = ArrayDeque<Outcome<ChatMessage>>()
    val reactions = mutableListOf<String>()
    val deleted = mutableListOf<Long>()
    var reactFailure: AppError? = null
    var deleteFailure: AppError? = null
    private var nextId = 200L

    override suspend fun post(crewId: Long, clientMessageId: String, content: String): Outcome<ChatMessage> {
        posts += clientMessageId to content
        return postResults.removeFirstOrNull()
            ?: Outcome.Success(chatMessage(nextId++, content, isMine = true, clientMessageId = clientMessageId))
    }

    override suspend fun history(crewId: Long, before: Long?, after: Long?, size: Int): Outcome<ChatPage> {
        historyCalls += Triple(before, after, size)
        return historyResults.removeFirstOrNull() ?: Outcome.Success(ChatPage.EMPTY)
    }

    override suspend fun react(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> {
        reactions += "react:$messageId:$kind"
        reactFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(ChatReactions(messageId, mapOf(kind to 1), setOf(kind)))
    }

    override suspend fun unreact(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> {
        reactions += "unreact:$messageId:$kind"
        reactFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(ChatReactions(messageId, emptyMap(), emptySet()))
    }

    override suspend fun delete(messageId: Long): Outcome<Unit> {
        deleted += messageId
        return deleteFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }
}

class FakeLiveChannel : LiveChannel {
    val presences = mutableListOf<ChatPresence>()
    var closes = 0
    override val messages = MutableSharedFlow<ServerMessage>()
    override val state = MutableStateFlow(LiveState.CONNECTED)

    override suspend fun connect(): Outcome<Unit> = Outcome.Success(Unit)

    override fun send(m: ClientMessage) {
        if (m is ChatPresence) presences += m
    }

    override fun close() {
        closes++
    }
}

class FakeLiveChannelProvider(val channel: FakeLiveChannel = FakeLiveChannel()) : LiveChannelProvider {
    var opens = 0

    override fun open(): LiveChannel {
        opens++
        return channel
    }
}

class FakeOpenChatRoomRegistry : OpenChatRoomRegistry {
    val opened = mutableListOf<Long>()
    val closed = mutableListOf<Long>()

    override fun opened(crewId: Long) {
        opened += crewId
    }

    override fun closed(crewId: Long) {
        closed += crewId
    }
}

class FakeReportRepository : ReportRepository {
    val calls = mutableListOf<Triple<ReportTarget, ReportReason, String?>>()
    var failure: AppError? = null

    override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?): Outcome<ReportStatus> {
        calls += Triple(target, reason, detail)
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(ReportStatus.RECEIVED)
    }
}
