package com.a207.runkk.app.api.chat

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.chat.ChatAuthorRole
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatMessageKind
import com.a207.runkk.app.domain.chat.ChatReactions
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import retrofit2.create

/** api.chat(6.1.14, F.2.2 크루 채팅, FR-208~FR-213, D-101): MockWebServer 응답 → 도메인, 요청 경로·본문. S3b 구현 전이라 05-03·S0 DTO 기준. */
class ChatRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = ChatRepositoryImpl(api.retrofit.create())

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `지난 메시지 쪽을 오래된 → 새 순으로 해독하고 before·after·size를 질의로 보낸다`() = runTest {
        api.reply(
            """{"messages":[{"messageId":101,"authorId":null,"authorName":"탈퇴한 사용자","content":"안녕",
               |"sentAt":"2026-10-22T19:00:00+09:00","reactions":{"LIKE":3,"HEART":0,"FIRE":9},"myReactions":["LIKE","FIRE"],
               |"mine":false,"canDelete":true,"clientMessageId":null},
               |{"messageId":102,"authorId":7,"authorName":"나","content":"반가워","sentAt":"2026-10-22T19:00:05+09:00",
               |"reactions":{},"myReactions":[],"mine":true,"canDelete":true,"clientMessageId":"c-2"}],"hasMore":true}
            """.trimMargin()
        )
        api.reply("""{"messages":[],"hasMore":false}""")

        val page = repository.history(5, before = null, after = null, size = 50).value()
        repository.history(5, before = null, after = 150, size = 50).value()

        assertEquals(true, page.hasMore)
        assertEquals(listOf(101L, 102L), page.messages.map { it.messageId })
        val first = page.messages.first()
        assertNull(first.authorId)
        assertEquals(Instant.parse("2026-10-22T10:00:00Z"), first.sentAt)
        assertEquals(mapOf(ReactionKind.LIKE to 3, ReactionKind.HEART to 0), first.reactions, "모르는 종류는 건너뛴다")
        assertEquals(setOf(ReactionKind.LIKE), first.myReactions)
        assertEquals(false, first.isMine)
        assertEquals(true, page.messages[1].isMine)
        assertEquals("c-2", page.messages[1].clientMessageId)
        assertEquals(
            "/api/v1/crews/5/chat/messages?size=50",
            api.take().url.let {
                "${it.encodedPath}?${it.encodedQuery}"
            }
        )
        assertEquals(
            "/api/v1/crews/5/chat/messages?after=150&size=50",
            api.take().url.let {
                "${it.encodedPath}?${it.encodedQuery}"
            }
        )
    }

    @Test
    fun `보내기는 clientMessageId와 내용을 보내고 서버 메시지를 돌려준다`() = runTest {
        api.reply(
            """{"messageId":103,"authorId":7,"authorName":"나","content":"안녕","sentAt":"2026-10-22T19:01:00+09:00",
               |"reactions":{},"myReactions":[],"mine":true,"canDelete":true,"clientMessageId":"c-3"}
            """.trimMargin(),
            code = 201
        )

        val posted = repository.post(5, "c-3", "안녕").value()

        assertEquals(
            ChatMessage(
                messageId = 103,
                authorId = 7,
                authorName = "나",
                content = "안녕",
                sentAt = Instant.parse("2026-10-22T10:01:00Z"),
                reactions = emptyMap(),
                myReactions = emptySet(),
                isMine = true,
                canDelete = true,
                clientMessageId = "c-3"
            ),
            posted
        )
        val request = api.take()
        assertEquals("POST /api/v1/crews/5/chat/messages", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement("""{"clientMessageId":"c-3","content":"안녕"}"""),
            Json.parseToJsonElement(request.body!!.utf8())
        )
    }

    @Test
    fun `리액션 더하기·빼기는 종류 이름을 경로에 싣고 본인 상태를 돌려준다`() = runTest {
        api.reply("""{"messageId":101,"reactions":{"LIKE":4,"HEART":1},"myReactions":["LIKE"]}""")
        api.reply("""{"messageId":101,"reactions":{"LIKE":3,"HEART":1},"myReactions":[]}""")

        val reacted = repository.react(101, ReactionKind.LIKE).value()
        val unreacted = repository.unreact(101, ReactionKind.LIKE).value()

        assertEquals(
            ChatReactions(101, mapOf(ReactionKind.LIKE to 4, ReactionKind.HEART to 1), setOf(ReactionKind.LIKE)),
            reacted
        )
        assertEquals(emptySet(), unreacted.myReactions)
        assertEquals(
            "PUT /api/v1/chat-messages/101/reactions/LIKE",
            api.take().let {
                "${it.method} ${it.url.encodedPath}"
            }
        )
        assertEquals(
            "DELETE /api/v1/chat-messages/101/reactions/LIKE",
            api.take().let {
                "${it.method} ${it.url.encodedPath}"
            }
        )
    }

    @Test
    fun `삭제는 204이고 거부는 사유 코드로 돌려준다`() = runTest {
        api.replyEmpty(204)
        api.reply("""{"code":"PERMISSION_DENIED","message":"권한 없음"}""", code = 403)
        api.reply("""{"code":"CHAT_EMPTY","message":"빈 메시지"}""", code = 409)

        assertEquals(Outcome.Success(Unit), repository.delete(101))
        assertEquals(Outcome.Failure(AppError.Rejected("PERMISSION_DENIED")), repository.delete(102))
        assertEquals(Outcome.Failure(AppError.Rejected("CHAT_EMPTY")), repository.post(5, "c-4", " "))
        assertEquals("DELETE /api/v1/chat-messages/101", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `같은 clientMessageId 재전송(200)은 처음 메시지로 읽고 속도 제한·크루 밖·없는 메시지는 사유 코드로 돌려준다`() = runTest {
        api.reply(
            """{"messageId":103,"authorId":7,"authorName":"나","content":"안녕","sentAt":"2026-10-22T19:01:00.250+09:00",
               |"reactions":{"LIKE":1},"myReactions":[],"mine":true,"canDelete":true,"clientMessageId":"c-3"}
            """.trimMargin(),
            code = 200
        )
        api.reply("""{"code":"TOO_MANY_ATTEMPTS","message":"잠시 뒤"}""", code = 429)
        api.reply("""{"code":"NOT_CREW_MEMBER","message":"크루원 아님"}""", code = 409)
        api.reply("""{"code":"CHAT_MESSAGE_NOT_FOUND","message":"메시지 없음"}""", code = 409)
        api.reply("""{"code":"BODY_TOO_LONG","message":"500자 초과"}""", code = 409)

        val resent = repository.post(5, "c-3", "안녕").value()
        assertEquals(103L, resent.messageId)
        assertEquals(Instant.parse("2026-10-22T10:01:00.250Z"), resent.sentAt)
        assertEquals(mapOf(ReactionKind.LIKE to 1), resent.reactions, "0인 종류는 키가 없다")
        assertEquals(Outcome.Failure(AppError.Rejected("TOO_MANY_ATTEMPTS")), repository.post(5, "c-4", "a"))
        assertEquals(Outcome.Failure(AppError.Rejected("NOT_CREW_MEMBER")), repository.history(6, null, null, 50))
        assertEquals(Outcome.Failure(AppError.Rejected("CHAT_MESSAGE_NOT_FOUND")), repository.delete(999))
        assertEquals(Outcome.Failure(AppError.Rejected("BODY_TOO_LONG")), repository.post(5, "c-5", "x".repeat(501)))
    }

    @Test
    fun `TC-6496 채팅 전송 형식에는 위치 항목이 없다`() {
        val names =
            listOf(ChatMessageItem.serializer(), ChatPostRequest.serializer(), ReactionResponse.serializer())
                .flatMap { d -> (0 until d.descriptor.elementsCount).map(d.descriptor::getElementName) }
        assertEquals(
            emptyList(),
            names.filter {
                it.lowercase() in setOf("latitude", "longitude", "location", "course")
            }
        )
    }

    @Test
    fun `W5 메시지 종류·작성자 직책·러닝 거리를 읽고 옛 응답은 TEXT·null로 읽는다`() = runTest {
        api.reply(
            """{"messages":[{"messageId":201,"authorId":4,"authorName":"느긋한고래","content":"느긋한고래님이 5.02km를 달렸어요",
               |"sentAt":"2026-10-22T19:40:00+09:00","reactions":{"CHECK":2},"myReactions":["CHECK"],"mine":false,"canDelete":false,
               |"clientMessageId":null,"messageKind":"RUN_FINISHED","authorRole":"MEMBER","runDistanceMeters":5021},
               |{"messageId":202,"authorId":1,"authorName":"새벽곰","content":"공지","sentAt":"2026-10-22T19:41:00+09:00",
               |"messageKind":"TEXT","authorRole":"LEADER","runDistanceMeters":null},
               |{"messageId":203,"authorId":2,"authorName":"옛응답","content":"안녕","sentAt":"2026-10-22T19:42:00+09:00"}],
               |"hasMore":false}
            """.trimMargin()
        )

        val messages = repository.history(5, before = null, after = null, size = 50).value().messages

        val run = messages[0]
        assertEquals(ChatMessageKind.RUN_FINISHED, run.kind)
        assertEquals(Distance(5_021), run.runDistance)
        assertEquals(ChatAuthorRole.MEMBER, run.authorRole)
        assertEquals(mapOf(ReactionKind.CHECK to 2), run.reactions)
        assertEquals(setOf(ReactionKind.CHECK), run.myReactions)
        assertEquals(true, run.isSystem)
        assertEquals(ChatAuthorRole.LEADER, messages[1].authorRole)
        assertEquals(true, messages[1].isLeaderBadge)
        assertEquals(ChatMessageKind.TEXT, messages[2].kind)
        assertNull(messages[2].authorRole)
        assertNull(messages[2].runDistance)
    }

    @Test
    fun `반응한 사람 목록을 먼저 단 순서로 읽고 모르는 종류는 건너뛴다`() = runTest {
        api.reply(
            """{"messageId":801,"reactors":[
               |{"memberId":4,"displayName":"느긋한고래","kind":"CHECK","reactedAt":"2026-10-22T19:37:00+09:00","me":true},
               |{"memberId":3,"displayName":"새벽곰","kind":"LIKE","reactedAt":"2026-10-22T19:38:00+09:00","me":false},
               |{"memberId":5,"displayName":"미래","kind":"FIRE","reactedAt":"2026-10-22T19:39:00+09:00","me":false}]}
            """.trimMargin()
        )

        val reactors = repository.reactors(801).value()

        assertEquals(
            listOf(
                Reactor(4, "느긋한고래", ReactionKind.CHECK, Instant.parse("2026-10-22T10:37:00Z"), isMe = true),
                Reactor(3, "새벽곰", ReactionKind.LIKE, Instant.parse("2026-10-22T10:38:00Z"), isMe = false)
            ),
            reactors
        )
        assertEquals("/api/v1/chat-messages/801/reactions", api.take().url.encodedPath)
    }
}
