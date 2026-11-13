package com.a207.runkk.app.domain.chat

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.testing.FakeLiveChannelProvider
import com.a207.runkk.app.domain.testing.T0
import com.a207.runkk.app.domain.testing.TestClock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** ChatRoom(6.1.14 domain.chat, D-101, D-103, D-130, D-131, D-160): 시험 문서 2.2.16 TC-6481~TC-6498 중 domain 대상. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomTest {
    private val chats = FakeChatRepository()
    private val live = FakeLiveChannelProvider()
    private val registry = FakeOpenChatRoomRegistry()
    private val clock = TestClock()

    private fun TestScope.room(): ChatRoom = ChatRoom(chats, live, registry, clock, backgroundScope)

    private fun ids(room: ChatRoom): List<Long?> = room.messages.value.messages.map { it.messageId }

    private fun TestScope.opened(vararg history: ChatPage): ChatRoom {
        history.forEach { chats.historyResults.addLast(Outcome.Success(it)) }
        return room().also {
            it.open(5)
            runCurrent()
        }
    }

    @Test
    fun `TC-6481 방을 열면 최근 50건을 읽고 임대를 받아 입장 신호를 보낸다`() = runTest {
        val room = opened(page(51L..100L, hasMore = true))
        assertEquals(listOf(HistoryCall(5, null, null, 50)), chats.historyCalls)
        assertEquals(1, live.opens)
        assertEquals(listOf(ChatPresence(5, true)), live.channel.presences)
        assertEquals(listOf(5L), registry.opened)
        assertEquals((51L..100L).toList(), ids(room))
        assertTrue(room.messages.value.hasMore)
    }

    @Test
    fun `TC-6482 그 크루의 채팅 메시지만 목록 끝에 바로 넣는다`() = runTest {
        val room = opened(page(51L..100L))
        live.channel.messages.emit(posted(5, 101))
        live.channel.messages.emit(posted(6, 201))
        live.channel.messages.emit(
            OpponentProgress(7, "상대", Distance(10), Duration.ofSeconds(5), 3, OpponentProgress.Connection.CONNECTED)
        )
        runCurrent()
        assertEquals((51L..101L).toList(), ids(room))
        assertEquals(1, chats.historyCalls.size)
    }

    @Test
    fun `TC-6483 보내기는 낙관적 항목을 넣고 에코가 오면 서버 메시지로 바꾼다`() = runTest {
        val room = opened(page(51L..100L))
        chats.postGate = CompletableDeferred()
        room.send("안녕")
        val pending = room.messages.value.messages.last()
        assertEquals(ChatMessage.SendState.SENDING, pending.sendState)
        runCurrent()
        val clientId = requireNotNull(pending.clientMessageId)
        assertEquals(listOf(Triple(5L, clientId, "안녕")), chats.posts)
        chats.postGate?.complete(Unit)
        runCurrent()
        live.channel.messages.emit(posted(5, 102, clientMessageId = clientId))
        runCurrent()
        val mine = room.messages.value.messages.filter { it.clientMessageId == clientId }
        assertEquals(1, mine.size)
        assertEquals(102L, mine.single().messageId)
        assertTrue(mine.single().isMine)
    }

    @Test
    fun `TC-6484 에코가 응답보다 먼저 와도 항목은 하나다`() = runTest {
        val room = opened(page(51L..100L))
        chats.postGate = CompletableDeferred()
        room.send("안녕")
        runCurrent()
        val clientId = requireNotNull(chats.posts.single().second)
        live.channel.messages.emit(posted(5, 102, clientMessageId = clientId))
        runCurrent()
        chats.postGate?.complete(Unit)
        runCurrent()
        assertEquals(1, room.messages.value.messages.count { it.messageId == 102L })
        assertEquals(51, room.messages.value.messages.size)
    }

    @Test
    fun `TC-6485 보내기 실패는 FAILED로 남기고 retry는 같은 clientMessageId로 다시 보낸다`() = runTest {
        val room = opened(page(51L..100L))
        chats.postResults.addLast(Outcome.Failure(AppError.Network))
        room.send("안녕")
        runCurrent()
        val failed = room.messages.value.messages.last()
        assertEquals(ChatMessage.SendState.FAILED, failed.sendState)
        room.retry(requireNotNull(failed.clientMessageId))
        runCurrent()
        assertEquals(2, chats.posts.size)
        assertEquals(chats.posts[0].second, chats.posts[1].second)
        assertEquals(ChatMessage.SendState.SENT, room.messages.value.messages.last().sendState)
        assertEquals(51, room.messages.value.messages.size)
    }

    @Test
    fun `TC-6487 본인 리액션은 REST 응답으로, 수는 실시간 메시지로 맞춘다`() = runTest {
        val room = opened(page(101L..101L))
        chats.reactResult = ChatReactions(101, mapOf(ReactionKind.LIKE to 1), setOf(ReactionKind.LIKE))
        room.react(101, ReactionKind.LIKE)
        runCurrent()
        assertEquals(setOf(ReactionKind.LIKE), room.messages.value.messages.single().myReactions)
        assertEquals(emptyMap(), room.messages.value.messages.single().reactions)
        live.channel.messages.emit(CrewChatReactionChanged(5, 101, mapOf("LIKE" to 4, "HEART" to 1)))
        runCurrent()
        assertEquals(
            mapOf(ReactionKind.LIKE to 4, ReactionKind.HEART to 1),
            room.messages.value.messages.single().reactions
        )
        chats.reactResult = ChatReactions(101, mapOf(ReactionKind.LIKE to 3), emptySet())
        room.unreact(101, ReactionKind.LIKE)
        runCurrent()
        assertEquals(emptySet(), room.messages.value.messages.single().myReactions)
    }

    @Test
    fun `TC-6488 삭제·숨김 메시지는 자리표시로 남기고 없는 ID는 무시한다`() = runTest {
        val room = opened(page(100L..102L))
        live.channel.messages.emit(CrewChatRemoved(5, 101, CrewChatRemoved.Reason.DELETED))
        live.channel.messages.emit(CrewChatRemoved(5, 102, CrewChatRemoved.Reason.HIDDEN))
        live.channel.messages.emit(CrewChatRemoved(5, 999, CrewChatRemoved.Reason.DELETED))
        runCurrent()
        val messages = room.messages.value.messages
        assertEquals(listOf(100L, 101L, 102L), messages.map { it.messageId })
        assertEquals(listOf(false, true, true), messages.map { it.isRemoved })
        assertEquals("", messages[1].content)
        assertEquals(emptyMap(), messages[2].reactions)
    }

    @Test
    fun `TC-6489 지울 수 있는 메시지만 지운다`() = runTest {
        val room = opened(ChatPage(listOf(message(101, canDelete = true), message(103, canDelete = false)), false))
        room.delete(101)
        room.delete(103)
        runCurrent()
        assertEquals(listOf(101L), chats.deleted)
        assertTrue(room.messages.value.messages.first().isRemoved)
    }

    @Test
    fun `TC-6490·TC-6491 다시 연결되면 마지막 ID 뒤를 메우고 입장 신호를 다시 보낸다`() = runTest {
        val room = opened(page(101L..150L))
        live.channel.messages.emit(posted(5, 151))
        runCurrent()
        chats.historyResults.addLast(Outcome.Success(page(151L..153L)))
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(HistoryCall(5, null, 131, 50), chats.historyCalls.last(), "마지막(151)에서 20개 앞(131)부터 다시 읽는다")
        assertEquals(2, live.channel.presences.count { it.isPresent })
        assertEquals((101L..153L).toList(), ids(room))
    }

    @Test
    fun `TC-6492 메우기는 hasMore가 거짓이 될 때까지 이어 읽는다`() = runTest {
        val room = opened(page(101L..150L))
        chats.historyResults.addLast(Outcome.Success(page(151L..200L, hasMore = true)))
        chats.historyResults.addLast(Outcome.Success(page(201L..250L, hasMore = true)))
        chats.historyResults.addLast(Outcome.Success(page(251L..270L, hasMore = false)))
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(listOf<Long?>(130, 200, 250), chats.historyCalls.drop(1).map { it.after })
        assertEquals((101L..270L).toList(), ids(room))

        chats.historyCalls.clear()
        chats.historyResults.addLast(Outcome.Success(ChatPage.EMPTY))
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(1, chats.historyCalls.size)
    }

    @Test
    fun `메우기는 20개 앞부터 다시 읽어 늦게 커밋된 메시지를 채우고 중복은 하나로 합친다(총괄 결정)`() = runTest {
        val room = opened(page((101L..140L) + (142L..150L)))
        chats.historyResults.addLast(Outcome.Success(page(130L..152L)))
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(129L, chats.historyCalls.last().after, "받은 49건 가운데 끝에서 20개 앞")
        assertEquals((101L..152L).toList(), ids(room), "늦게 커밋된 141을 채우고 중복 0")
    }

    @Test
    fun `받은 메시지가 20건 이하면 처음부터 다시 읽는다`() = runTest {
        opened(page(101L..110L))
        chats.historyResults.addLast(Outcome.Success(ChatPage.EMPTY))
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(100L, chats.historyCalls.last().after)
    }

    @Test
    fun `TC-6493 폴링 모드면 2초마다 after로 읽고 닫으면 멈춘다`() = runTest {
        val room = opened(page(101L..150L))
        live.channel.state.value = LiveState.POLLING
        runCurrent()
        advanceTimeBy(6_001)
        assertEquals(3, chats.historyCalls.count { it.after == 130L })
        room.close()
        advanceTimeBy(10_000)
        assertEquals(4, chats.historyCalls.size)
    }

    @Test
    fun `AN5-C-5 연결(핸드셰이크)이 늦어도 첫 쪽을 먼저 읽어 보여 준다`() = runTest {
        live.channel.state.value = LiveState.CONNECTING
        live.channel.connectGate = CompletableDeferred()

        val room = opened(page(51L..100L))

        assertEquals(1, live.channel.connects)
        assertEquals((51L..100L).toList(), ids(room), "connect가 끝나기 전에 첫 쪽이 보인다")
        live.channel.connectGate?.complete(Unit)
    }

    @Test
    fun `AN5-C-5 연결이 CLOSED가 되면(다른 기기가 연결을 가져감) 2초마다 REST로 읽어 잇는다`() = runTest {
        val room = opened(page(101L..150L))

        live.channel.state.value = LiveState.CLOSED
        runCurrent()
        advanceTimeBy(4_001)

        assertEquals(2, chats.historyCalls.count { it.after == 130L })
        room.close()
    }

    @Test
    fun `AN5-C-5 connect가 실패해 상태가 CONNECTING에 머물러도 2초마다 REST로 읽어 잇는다`() = runTest {
        live.channel.state.value = LiveState.CONNECTING
        live.channel.connectFailure = AppError.Unauthorized
        val room = opened(page(101L..150L))

        advanceTimeBy(4_001)

        assertEquals(2, chats.historyCalls.count { it.after == 130L })
        room.close()
    }

    @Test
    fun `TC-6494 닫으면 퇴장 신호를 보낸 뒤 자기 임대만 반납한다`() = runTest {
        val room = opened(page(101L..101L))
        room.close()
        assertEquals(listOf("send:${ChatPresence(5, false)}", "close"), live.channel.events.takeLast(2))
        assertEquals(listOf(5L), registry.closed)
    }

    @Test
    fun `TC-6496 채팅 형식에 위치 항목이 없다`() {
        val forbidden = listOf("lat", "lng", "latitude", "longitude", "location", "geo")
        val fields =
            listOf(ChatPresence::class.java, ChatMessage::class.java, CrewChatPosted::class.java)
                .flatMap { type -> type.declaredFields.map { it.name.lowercase() } }
        assertTrue(fields.none { name -> forbidden.any { name.contains(it) } }, "$fields")
    }

    @Test
    fun `TC-6497 두 번 다시 보내도 clientMessageId와 항목은 하나다`() = runTest {
        val room = opened(page(101L..101L))
        chats.postResults.addLast(Outcome.Failure(AppError.Network))
        chats.postResults.addLast(Outcome.Failure(AppError.Server(503)))
        room.send("안녕")
        runCurrent()
        val clientId = requireNotNull(room.messages.value.messages.last().clientMessageId)
        room.retry(clientId)
        runCurrent()
        assertEquals(ChatMessage.SendState.FAILED, room.messages.value.messages.last().sendState)
        room.retry(clientId)
        runCurrent()
        assertEquals(setOf(clientId), chats.posts.map { it.second }.toSet())
        assertEquals(3, chats.posts.size)
        assertEquals(ChatMessage.SendState.SENT, room.messages.value.messages.last().sendState)
        assertEquals(2, room.messages.value.messages.size)
    }

    @Test
    fun `TC-6498 입장 신호는 연결마다 한 번이고 open이 겹쳐도 다시 보내지 않는다`() = runTest {
        live.channel.state.value = LiveState.CONNECTING
        val connecting = opened(page(101L..101L))
        assertEquals(0, live.channel.presences.size)
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        connecting.open(5)
        runCurrent()
        assertEquals(1, live.channel.presences.size)
        connecting.close()

        live.channel.presences.clear()
        chats.historyResults.addLast(Outcome.Success(page(101L..101L)))
        val connected = room()
        connected.open(5)
        runCurrent()
        connected.open(5)
        runCurrent()
        assertEquals(listOf(ChatPresence(5, true)), live.channel.presences)
    }

    @Test
    fun `첫 쪽 읽기가 실패하면 다음 연결 때 다시 읽는다`() = runTest {
        live.channel.state.value = LiveState.CONNECTING
        chats.historyResults.addLast(Outcome.Failure(AppError.Network))
        chats.historyResults.addLast(Outcome.Success(page(51L..100L)))
        val room = room().also { it.open(5) }
        runCurrent()
        assertEquals(emptyList(), ids(room))
        live.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertEquals(listOf(HistoryCall(5, null, null, 50), HistoryCall(5, null, null, 50)), chats.historyCalls)
        assertEquals((51L..100L).toList(), ids(room))
    }

    @Test
    fun `위로 끌면 가장 오래된 메시지 앞 쪽을 before로 읽어 앞에 붙이고 끝이면 더 읽지 않는다(ui·chat)`() = runTest {
        val room = opened(page(51L..100L, hasMore = true))
        chats.historyResults.addLast(Outcome.Success(page(1L..50L, hasMore = false)))
        room.loadOlder()
        runCurrent()
        assertEquals(HistoryCall(5, 51, null, 50), chats.historyCalls.last())
        assertEquals((1L..100L).toList(), ids(room))
        assertEquals(false, room.messages.value.hasMore)
        room.loadOlder()
        runCurrent()
        assertEquals(2, chats.historyCalls.size)
    }

    @Test
    fun `AN5-C-6 이전 쪽 읽기가 시작도 못 하고 방이 닫혀도 다시 열면 이전 쪽을 읽는다`() = runTest {
        val room = opened(page(51L..100L, hasMore = true))
        room.loadOlder()
        room.close()
        runCurrent()
        chats.historyResults.addLast(Outcome.Success(page(51L..100L, hasMore = true)))
        room.open(5)
        runCurrent()
        val before = chats.historyCalls.size

        room.loadOlder()
        runCurrent()

        assertEquals(before + 1, chats.historyCalls.size, "겹침 방지 표지가 풀려 있다")
        assertEquals(51L, chats.historyCalls.last().before)
    }

    @Test
    fun `리액션·삭제·이전 쪽 읽기 실패는 failures로 화면에 알리고 목록은 그대로 둔다(A4)`() = runTest {
        val room = opened(ChatPage(listOf(message(101, canDelete = true)), hasMore = true))
        val seen = mutableListOf<ChatRoom.Failure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { room.failures.collect { seen += it } }
        chats.reactFailure = AppError.Network
        room.react(101, ReactionKind.LIKE)
        runCurrent()
        room.unreact(101, ReactionKind.LIKE)
        runCurrent()
        chats.deleteFailure = AppError.Rejected("CHAT_MESSAGE_NOT_FOUND")
        room.delete(101)
        runCurrent()
        chats.historyResults.addLast(Outcome.Failure(AppError.Network))
        room.loadOlder()
        runCurrent()
        assertEquals(
            listOf(
                ChatRoom.Failure(ChatRoom.Failure.Action.REACT, AppError.Network),
                ChatRoom.Failure(ChatRoom.Failure.Action.REACT, AppError.Network),
                ChatRoom.Failure(ChatRoom.Failure.Action.DELETE, AppError.Rejected("CHAT_MESSAGE_NOT_FOUND")),
                ChatRoom.Failure(ChatRoom.Failure.Action.LOAD_OLDER, AppError.Network)
            ),
            seen
        )
        assertEquals(false, room.messages.value.messages.single().isRemoved)
        assertTrue(room.messages.value.hasMore)
    }

    @Test
    fun `같은 방을 닫았다 다시 열면 목록을 지우지 않고 메우며 보내던 메시지는 실패로 남겨 다시 보낼 수 있다`() = runTest {
        val room = opened(page(101L..101L))
        chats.postGate = CompletableDeferred()
        room.send("안녕")
        runCurrent()
        room.close()
        val pending = room.messages.value.messages.last()
        assertEquals(ChatMessage.SendState.FAILED, pending.sendState, "닫혀서 끊긴 보내기")
        room.open(5)
        runCurrent()
        assertEquals(listOf<Long?>(101, null), ids(room), "목록을 비우지 않는다")
        assertEquals(HistoryCall(5, null, 100, 50), chats.historyCalls.last(), "최근 쪽이 아니라 마지막 뒤를 메운다")
        chats.postGate = null
        room.retry(requireNotNull(pending.clientMessageId))
        runCurrent()
        assertEquals(ChatMessage.SendState.SENT, room.messages.value.messages.last().sendState)
    }

    @Test
    fun `다른 크루 방을 열면 목록을 비운다`() = runTest {
        val room = opened(page(101L..101L))
        room.close()
        chats.historyResults.addLast(Outcome.Success(page(201L..201L)))
        room.open(6)
        runCurrent()
        assertEquals(listOf<Long?>(201), ids(room))
    }

    // ---------- 도구 ----------

    private fun message(id: Long, canDelete: Boolean = false) = ChatMessage(
        messageId = id,
        authorId = 9,
        authorName = "러너",
        content = "m$id",
        sentAt = T0,
        reactions = emptyMap(),
        myReactions = emptySet(),
        isMine = false,
        canDelete = canDelete,
        clientMessageId = null
    )

    private fun page(range: Iterable<Long>, hasMore: Boolean = false) = ChatPage(range.map { message(it) }, hasMore)

    private fun posted(crewId: Long, id: Long, clientMessageId: String? = null) =
        CrewChatPosted(crewId, id, 9, "러너", "m$id", T0, clientMessageId)
}

private data class HistoryCall(val crewId: Long, val before: Long?, val after: Long?, val size: Int)

private class FakeChatRepository : ChatRepository {
    val historyCalls = mutableListOf<HistoryCall>()
    val historyResults = ArrayDeque<Outcome<ChatPage>>()
    val posts = mutableListOf<Triple<Long, String, String>>()
    val postResults = ArrayDeque<Outcome<ChatMessage>>()
    var postGate: CompletableDeferred<Unit>? = null
    var reactResult: ChatReactions? = null
    var reactFailure: AppError? = null
    var deleteFailure: AppError? = null
    val deleted = mutableListOf<Long>()
    private var nextId = 102L

    override suspend fun post(crewId: Long, clientMessageId: String, content: String): Outcome<ChatMessage> {
        posts += Triple(crewId, clientMessageId, content)
        postGate?.await()
        return postResults.removeFirstOrNull() ?: Outcome.Success(
            ChatMessage(
                messageId = nextId++,
                authorId = 1,
                authorName = "나",
                content = content,
                sentAt = T0,
                reactions = emptyMap(),
                myReactions = emptySet(),
                isMine = true,
                canDelete = true,
                clientMessageId = clientMessageId
            )
        )
    }

    override suspend fun history(crewId: Long, before: Long?, after: Long?, size: Int): Outcome<ChatPage> {
        historyCalls += HistoryCall(crewId, before, after, size)
        return historyResults.removeFirstOrNull() ?: Outcome.Success(ChatPage.EMPTY)
    }

    override suspend fun react(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> =
        reactFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(requireNotNull(reactResult))

    override suspend fun unreact(messageId: Long, kind: ReactionKind): Outcome<ChatReactions> =
        reactFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(requireNotNull(reactResult))

    override suspend fun reactors(messageId: Long): Outcome<List<Reactor>> = Outcome.Success(emptyList())

    override suspend fun delete(messageId: Long): Outcome<Unit> {
        deleted += messageId
        return deleteFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }
}

private class FakeOpenChatRoomRegistry : OpenChatRoomRegistry {
    val opened = mutableListOf<Long>()
    val closed = mutableListOf<Long>()

    override fun opened(crewId: Long) {
        opened += crewId
    }

    override fun closed(crewId: Long) {
        closed += crewId
    }
}
