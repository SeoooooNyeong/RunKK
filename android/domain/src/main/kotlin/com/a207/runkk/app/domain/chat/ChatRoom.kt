package com.a207.runkk.app.domain.chat

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 한 크루 채팅방의 메시지 목록을 유지한다(FR-208~FR-213, D-101, D-103, D-130, D-131, D-160). 목록은 늘 오래된 → 새 순이다.
 * REST 쪽(ChatRepository)과 실시간 연결의 임대(LiveChannelProvider.open)를 합친다. 실시간 연결은 대결·크루런과 함께 쓰므로
 * close()는 자기 임대만 반납한다. 화면(ui.chat)마다 하나를 만든다.
 *
 * 연결 상태(LiveChannel.state)를 따른다: CONNECTED에 들어갈 때마다 입장 신호(ChatPresence)를 한 번 보내고, 다시 들어온
 * 것이면 메운다. POLLING이거나 실시간이 닫혔으면(CLOSED, 연결 실패) 2초마다 after로 읽는다. 메우기는 마지막으로 받은 메시지에서 [REFILL_OVERLAP]개 앞의 messageId부터
 * after로 다시 읽어 messageId로 중복을 걸러 합친다(서버 ID 발급 순서와 커밋 순서가 달라 생기는 구멍 방지, 총괄 결정). 이어 읽기는
 * hasMore가 거짓이 될 때까지 마지막 messageId 뒤를 읽는다.
 */
class ChatRoom
@Inject
constructor(
    private val chats: ChatRepository,
    private val channels: LiveChannelProvider,
    private val openRooms: OpenChatRoomRegistry,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var room: OpenRoom? = null

    /** 메우기·폴링이 겹쳐 같은 쪽을 두 번 읽지 않게 한다. */
    private val filling = Mutex()

    /** 이전 쪽 읽기가 겹치지 않게 한다(loadOlder). */
    private val loadingOlder = AtomicBoolean(false)

    private val page = MutableStateFlow(ChatPage.EMPTY)

    /** [page]가 담은 방의 크루. 같은 크루 방을 닫았다 다시 열면 목록을 비우지 않고 이어서 메운다. */
    private var pageCrewId: Long? = null

    /** 화면 상태의 근거. */
    val messages: StateFlow<ChatPage> = page.asStateFlow()

    private val _failures = MutableSharedFlow<Failure>(extraBufferCapacity = FAILURE_BUFFER)

    /**
     * 화면에 알릴 실패(리액션·삭제·이전 쪽 읽기, A4). 목록은 바꾸지 않으므로 화면이 안내만 한다. 보내기 실패는 항목의
     * sendState(FAILED)로 보이고(D-160 (3)), 메우기·폴링 실패는 다음 연결·주기에 다시 읽으므로 알리지 않는다.
     */
    val failures: SharedFlow<Failure> = _failures.asSharedFlow()

    /**
     * 방을 연다. 같은 크루 방이 이미 열려 있으면 아무것도 하지 않는다(입장 신호를 다시 보내지 않는다, D-160 (4)).
     * 같은 크루 방을 닫았다 다시 열면(화면이 가려졌다 돌아옴) 목록을 지우지 않고 마지막 메시지 뒤를 메운다.
     */
    fun open(crewId: Long) {
        val opened =
            synchronized(lock) {
                if (room?.crewId == crewId) return
                room?.let(::shutDown)
                val roomScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
                if (pageCrewId != crewId) {
                    page.value = ChatPage.EMPTY
                    pageCrewId = crewId
                }
                val isReopen = page.value.messages.any { it.messageId != null }
                openRooms.opened(crewId)
                OpenRoom(crewId, channels.open(), roomScope, isReopen).also { room = it }
            }
        opened.scope.launch { start(opened) }
    }

    /** 낙관적으로 목록에 넣고 보낸다. 서버 에코(CrewChatPosted)나 응답이 오면 같은 clientMessageId 항목을 바꾼다. */
    fun send(content: String) {
        val opened = synchronized(lock) { room } ?: return
        val clientMessageId = UUID.randomUUID().toString()
        val pending =
            ChatMessage(
                messageId = null,
                authorId = null,
                authorName = "",
                content = content,
                sentAt = clock.instant(),
                reactions = emptyMap(),
                myReactions = emptySet(),
                isMine = true,
                canDelete = false,
                clientMessageId = clientMessageId,
                sendState = ChatMessage.SendState.SENDING
            )
        page.update { it.copy(messages = it.messages + pending) }
        opened.scope.launch { post(opened.crewId, clientMessageId, content) }
    }

    /** 보내기에 실패한 메시지를 같은 clientMessageId로 다시 보낸다(서버 멱등, D-160 (3)). */
    fun retry(clientMessageId: String) {
        val opened = synchronized(lock) { room } ?: return
        val failed =
            page.value.messages.firstOrNull {
                it.clientMessageId == clientMessageId && it.sendState == ChatMessage.SendState.FAILED
            } ?: return
        replaceByClientId(clientMessageId) { it.copy(sendState = ChatMessage.SendState.SENDING) }
        opened.scope.launch { post(opened.crewId, clientMessageId, failed.content) }
    }

    /** 리액션 더하기. 본인 상태는 REST 응답으로, 수는 CrewChatReactionChanged로 맞춘다(FR-210). */
    fun react(messageId: Long, kind: ReactionKind) {
        val opened = synchronized(lock) { room } ?: return
        opened.scope.launch { applyMine(chats.react(messageId, kind)) }
    }

    fun unreact(messageId: Long, kind: ReactionKind) {
        val opened = synchronized(lock) { room } ?: return
        opened.scope.launch { applyMine(chats.unreact(messageId, kind)) }
    }

    /** 지울 수 있는 메시지(canDelete)만 지운다(FR-213). 지우면 자리표시로 바꾼다(D-160 (1)). */
    fun delete(messageId: Long) {
        val opened = synchronized(lock) { room } ?: return
        val target = page.value.messages.firstOrNull { it.messageId == messageId } ?: return
        if (!target.canDelete) return
        opened.scope.launch {
            when (val result = chats.delete(messageId)) {
                is Outcome.Success -> replaceById(messageId) { it.removed() }
                is Outcome.Failure -> _failures.tryEmit(Failure(Failure.Action.DELETE, result.error))
            }
        }
    }

    /**
     * 이전 쪽을 읽어 앞에 붙인다(ui.chat이 위로 끌 때, 6.1.14 "위로 끌면 before로 이전 페이지"). 가장 오래된 messageId를 before로
     * 읽고 응답의 hasMore로 더 있는지를 바꾼다. 더 없거나(hasMore 거짓) 이미 읽는 중이면 아무것도 하지 않는다.
     */
    fun loadOlder() {
        val opened = synchronized(lock) { room } ?: return
        val current = page.value
        if (!current.hasMore) return
        val oldest = current.messages.firstNotNullOfOrNull { it.messageId } ?: return
        if (!loadingOlder.compareAndSet(false, true)) return
        // 표지는 코루틴이 끝나면 푼다. 시작도 못 하고 취소돼도(방을 바로 닫음) invokeOnCompletion은 불린다(AN5-C-6).
        opened.scope.launch {
            when (val older = chats.history(opened.crewId, before = oldest, after = null, size = PAGE_SIZE)) {
                is Outcome.Success ->
                    page.update { merge(it.copy(hasMore = older.value.hasMore), older.value.messages) }

                is Outcome.Failure -> _failures.tryEmit(Failure(Failure.Action.LOAD_OLDER, older.error))
            }
        }.invokeOnCompletion { loadingOlder.set(false) }
    }

    /** 퇴장 신호를 보낸 뒤 자기 임대만 반납한다(D-103). */
    fun close() {
        synchronized(lock) {
            val closing = room ?: return
            room = null
            shutDown(closing)
        }
    }

    /** 퇴장 신호와 임대 반납. 보내는 중이던 메시지는 끊기므로 실패로 남겨 다시 보낼 수 있게 한다(같은 clientMessageId, D-160 (3)). */
    private fun shutDown(closing: OpenRoom) {
        page.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.sendState ==
                        ChatMessage.SendState.SENDING
                    ) {
                        it.copy(sendState = ChatMessage.SendState.FAILED)
                    } else {
                        it
                    }
                }
            )
        }
        closing.channel.send(ChatPresence(closing.crewId, isPresent = false))
        closing.channel.close()
        openRooms.closed(closing.crewId)
        closing.scope.cancel()
    }

    /**
     * 방 하나의 흐름. 받기 구독은 연결 전에 바로 시작한다(UNDISPATCHED: 첫 메시지를 놓치지 않게). 연결(핸드셰이크, 최대 3번 시도)은
     * 따로 띄워 첫 쪽 읽기가 기다리지 않게 한다. 첫 쪽 읽기가 실패하면 다음 CONNECTED 진입 때 다시 읽는다. 메우기·폴링은 따로 띄워
     * 다음 상태 변화가 기다리지 않게 하고 상태가 바뀌면 멈춘다.
     */
    private suspend fun start(opened: OpenRoom) {
        opened.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            opened.channel.messages.collect { onMessage(opened.crewId, it) }
        }
        val isConnectFailed = MutableStateFlow(false)
        opened.scope.launch {
            if (opened.channel.connect() is Outcome.Failure) isConnectFailed.value = true
        }
        var isLoaded = if (opened.isReopen) {
            fillAfterLast(opened.crewId)
            true
        } else {
            loadLatest(opened.crewId)
        }
        var hasConnected = false
        var work: Job? = null
        opened.channel.state.combine(isConnectFailed, ::effectiveState).collect { state ->
            work?.cancel()
            work = null
            when (state) {
                LiveState.CONNECTED -> {
                    opened.channel.send(ChatPresence(opened.crewId, isPresent = true))
                    val isReconnect = hasConnected
                    hasConnected = true
                    work =
                        opened.scope.launch {
                            if (!isLoaded) {
                                isLoaded = loadLatest(opened.crewId)
                            } else if (isReconnect) {
                                fillAfterLast(opened.crewId)
                            }
                        }
                }

                LiveState.POLLING, LiveState.CLOSED -> work = opened.scope.launch { pollEvery(opened.crewId) }

                LiveState.CONNECTING -> Unit
            }
        }
    }

    /**
     * 실시간을 더 기대할 수 없으면 CLOSED로 본다: 연결이 닫혔거나(1000 다른 기기의 새 연결, 1008·4401 갱신 거부) connect가 실패해
     * 상태가 CONNECTING에 머문 경우다. 그때 방은 REST 폴링으로 잇는다(AN5-C-5, D-131이 정하지 않은 CLOSED의 동작).
     */
    private fun effectiveState(state: LiveState, isConnectFailed: Boolean): LiveState =
        if (isConnectFailed && state == LiveState.CONNECTING) LiveState.CLOSED else state

    /** 최근 쪽(기본 50건)을 읽어 합친다. 성공하면 참. */
    private suspend fun loadLatest(crewId: Long): Boolean {
        val first = chats.history(crewId, before = null, after = null, size = PAGE_SIZE)
        if (first is Outcome.Success) {
            page.update {
                merge(it.copy(hasMore = first.value.hasMore), first.value.messages)
            }
        }
        return first is Outcome.Success
    }

    private suspend fun pollEvery(crewId: Long) {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            fillAfterLast(crewId, isOnePage = true)
        }
    }

    /**
     * 받은 메시지 뒤를 읽어 합친다. 첫 읽기는 [REFILL_OVERLAP]개 앞부터 다시 읽고, 폴링이 아니면 hasMore가 거짓이 될 때까지
     * 마지막 messageId 뒤를 이어 읽는다(D-160 (2)).
     */
    private suspend fun fillAfterLast(crewId: Long, isOnePage: Boolean = false) {
        filling.withLock {
            var after = refillAnchor()
            do {
                val result = chats.history(crewId, before = null, after = after, size = PAGE_SIZE)
                val more = result is Outcome.Success && result.value.hasMore && result.value.messages.isNotEmpty()
                if (result is Outcome.Success) page.update { merge(it, result.value.messages) }
                after = page.value.messages.mapNotNull { it.messageId }.maxOrNull()
            } while (more && !isOnePage)
        }
    }

    /** 메우기 시작점: 받은 메시지 ID 가운데 끝에서 [REFILL_OVERLAP]개 앞. 그보다 적게 받았으면 처음부터(첫 ID 앞), 없으면 null. */
    private fun refillAnchor(): Long? {
        val ids = page.value.messages.mapNotNull { it.messageId }.sorted()
        if (ids.isEmpty()) return null
        return if (ids.size > REFILL_OVERLAP) ids[ids.size - 1 - REFILL_OVERLAP] else ids.first() - 1
    }

    private suspend fun post(crewId: Long, clientMessageId: String, content: String) {
        when (val sent = chats.post(crewId, clientMessageId, content)) {
            is Outcome.Success -> page.update { merge(it, listOf(sent.value)) }

            is Outcome.Failure -> replaceByClientId(clientMessageId) {
                it.copy(sendState = ChatMessage.SendState.FAILED)
            }
        }
    }

    private fun onMessage(crewId: Long, m: ServerMessage) {
        // 닫혔거나 다른 크루 방으로 바뀐 뒤 늦게 도착한 메시지는 버린다.
        if (synchronized(lock) { room?.crewId } != crewId) return
        when (m) {
            is CrewChatPosted -> if (m.crewId == crewId) page.update { addPosted(it, m) }

            is CrewChatRemoved -> if (m.crewId == crewId) replaceById(m.messageId) { it.removed() }

            is CrewChatReactionChanged ->
                if (m.crewId == crewId) replaceById(m.messageId) { it.copy(reactions = m.reactions.toKinds()) }

            else -> Unit
        }
    }

    /** 실시간 새 메시지. 이미 받은 ID면(REST로 먼저 받음) 그대로 두고, 내 낙관적 항목이면 서버 메시지로 바꾼다. */
    private fun addPosted(current: ChatPage, m: CrewChatPosted): ChatPage =
        if (current.messages.any { it.messageId == m.messageId }) current else merge(current, listOf(m.toMessage()))

    private fun applyMine(result: Outcome<ChatReactions>) {
        if (result is Outcome.Failure) _failures.tryEmit(Failure(Failure.Action.REACT, result.error))
        if (result !is Outcome.Success) return
        replaceById(result.value.messageId) { it.copy(myReactions = result.value.myReactions) }
    }

    private fun replaceById(messageId: Long, change: (ChatMessage) -> ChatMessage) {
        page.update { current ->
            current.copy(messages = current.messages.map { if (it.messageId == messageId) change(it) else it })
        }
    }

    private fun replaceByClientId(clientMessageId: String, change: (ChatMessage) -> ChatMessage) {
        page.update { current ->
            current.copy(
                messages = current.messages.map { if (it.clientMessageId == clientMessageId) change(it) else it }
            )
        }
    }

    /**
     * 받은 메시지를 합친다: 같은 messageId는 하나만, 같은 clientMessageId의 낙관적 항목은 서버 메시지로 바꾼다. 서버 ID가 있는
     * 메시지는 ID 순, 아직 보내는 중인 항목은 그 뒤에 둔다. 내가 보낸 것(clientMessageId가 같은 항목)이면 isMine을 지킨다.
     */
    private fun merge(current: ChatPage, incoming: List<ChatMessage>): ChatPage {
        var messages = current.messages
        for (message in incoming) {
            val isMine = message.isMine || messages.any { it.sameAs(message) && it.isMine }
            val merged = message.copy(isMine = isMine, canDelete = message.canDelete || isMine)
            messages = messages.filterNot { it.sameAs(message) } + merged
        }
        val (confirmed, pending) = messages.partition { it.messageId != null }
        return current.copy(messages = confirmed.sortedBy { it.messageId } + pending)
    }

    private fun ChatMessage.sameAs(other: ChatMessage): Boolean = (messageId != null && messageId == other.messageId) ||
        (clientMessageId != null && clientMessageId == other.clientMessageId)

    /**
     * 실시간 새 메시지를 목록 항목으로 바꾼다. 본인 여부·삭제 권한은 REST 응답에만 있으므로 내가 보낸 항목과 맞을 때만 참이 된다(merge).
     * 운영진의 남의 메시지 삭제 권한은 다음 REST 조회 때 채워진다.
     */
    private fun CrewChatPosted.toMessage(): ChatMessage = ChatMessage(
        messageId = messageId,
        authorId = authorId,
        authorName = authorName,
        content = content,
        sentAt = sentAt,
        reactions = emptyMap(),
        myReactions = emptySet(),
        isMine = false,
        canDelete = false,
        clientMessageId = clientMessageId
    )

    private fun Map<String, Int>.toKinds(): Map<ReactionKind, Int> = entries.mapNotNull { (name, count) ->
        ReactionKind.entries.firstOrNull { it.name == name }?.let { it to count }
    }
        .toMap()

    /** 화면에 알릴 실패 하나: 어느 동작이 [error]로 실패했는가. */
    data class Failure(val action: Action, val error: AppError) {
        enum class Action { REACT, DELETE, LOAD_OLDER }
    }

    private class OpenRoom(
        val crewId: Long,
        val channel: LiveChannel,
        val scope: CoroutineScope,
        /** 같은 크루 방을 다시 연 것(목록이 남아 있다). */
        val isReopen: Boolean
    )

    private companion object {
        const val PAGE_SIZE = 50
        const val POLL_INTERVAL_MILLIS = 2_000L

        /** 메우기를 이만큼 앞의 메시지부터 다시 읽는다(커밋 순서 차이로 생긴 구멍을 메운다). */
        const val REFILL_OVERLAP = 20

        /** 화면이 잠깐 늦게 받아도 실패 알림을 잃지 않을 만큼. */
        const val FAILURE_BUFFER = 8
    }
}
