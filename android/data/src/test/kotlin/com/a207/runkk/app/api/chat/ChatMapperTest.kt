package com.a207.runkk.app.api.chat

import com.a207.runkk.app.core.model.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/** 채팅 메시지 항목과 반응자 행의 아바타 얼굴([임시:D-성-234], [임시:FR-성-24]). */
class ChatMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun 메시지_항목과_반응자_행의_얼굴을_옮긴다() {
        val (shown, withdrawn) = json.decodeFromString<ChatPageResponse>(
            """{"messages":[
               |{"messageId":801,"authorId":3,"authorName":"새벽곰","content":"안녕","sentAt":"2026-10-22T19:07:03+09:00",
               | "runDistanceMeters":null,"authorFaceKey":"head:3:ab12","authorShownGender":"MALE"},
               |{"messageId":802,"authorId":null,"authorName":"탈퇴한 사용자","content":"먼저 갈게요",
               | "sentAt":"2026-10-22T19:08:03+09:00","authorFaceKey":null,"authorShownGender":null}],"hasMore":false}
            """.trimMargin()
        ).toDomain().messages
        val (reactor, plain) = json.decodeFromString<ReactorListResponse>(
            """{"messageId":801,"reactors":[
               |{"memberId":4,"displayName":"느긋한고래","kind":"CHECK","reactedAt":"2026-10-22T19:37:00+09:00","me":true,
               | "faceKey":"default:FOX","shownGender":"FEMALE"},
               |{"memberId":3,"displayName":"새벽곰","kind":"LIKE","reactedAt":"2026-10-22T19:38:00+09:00","me":false,
               | "faceKey":null,"shownGender":"MALE"}]}
            """.trimMargin()
        ).toDomain()

        assertEquals("head:3:ab12", shown.authorFaceKey)
        assertEquals(Gender.MALE, shown.authorShownGender)
        assertNull(withdrawn.authorFaceKey, "탈퇴한 작성자는 둘 다 null")
        assertNull(withdrawn.authorShownGender)
        assertEquals("default:FOX", reactor.faceKey)
        assertEquals(Gender.FEMALE, reactor.shownGender)
        assertNull(plain.faceKey)
        assertEquals(Gender.MALE, plain.shownGender)
    }

    @Test
    fun 얼굴_항목이_없는_옛_응답도_읽는다() {
        val old = json.decodeFromString<ChatMessageItem>(
            """{"messageId":801,"authorId":3,"authorName":"새벽곰","content":"안녕","sentAt":"2026-10-22T19:07:03+09:00"}"""
        ).toDomain()
        val reactor = json.decodeFromString<ReactorListResponse>(
            """{"messageId":801,"reactors":[
               |{"memberId":4,"displayName":"a","kind":"CHECK","reactedAt":"2026-10-22T19:37:00+09:00","me":true}]}
            """.trimMargin()
        ).toDomain().single()

        assertNull(old.authorFaceKey)
        assertNull(old.authorShownGender)
        assertNull(reactor.faceKey)
        assertNull(reactor.shownGender)
    }
}
