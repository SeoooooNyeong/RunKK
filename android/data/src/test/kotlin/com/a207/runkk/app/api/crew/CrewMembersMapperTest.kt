package com.a207.runkk.app.api.crew

import com.a207.runkk.app.core.model.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/** 크루원 목록 행의 아바타 얼굴([임시:D-성-234], [임시:FR-성-24]): 서버 faceKey·shownGender를 도메인으로 옮긴다. */
class CrewMembersMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun members(body: String) = json.decodeFromString<CrewMembersResponse>(body).toDomain().members

    @Test
    fun `members_응답의_faceKey와_shownGender를_옮긴다`() {
        val (picked, plain) = members(
            """{"members":[
               |{"memberId":12,"displayName":"달리는곰","role":"LEADER","me":true,"genderVisibility":null,
               | "faceKey":"head:12:ab12","shownGender":"FEMALE"},
               |{"memberId":31,"displayName":"새벽러너","role":"MEMBER","me":false,"genderVisibility":null,
               | "faceKey":null,"shownGender":"MALE"}]}
            """.trimMargin()
        )

        assertEquals("head:12:ab12", picked.faceKey)
        assertEquals(Gender.FEMALE, picked.shownGender)
        assertNull(plain.faceKey, "얼굴을 안 골랐거나 볼 수 없는 크루원")
        assertEquals(Gender.MALE, plain.shownGender)
    }

    @Test
    fun `서버가_faceKey를_안_주면_null이다`() {
        val old = members("""{"members":[{"memberId":31,"displayName":"새벽러너","role":"MEMBER"}]}""").single()

        assertNull(old.faceKey)
        assertNull(old.shownGender)
    }

    @Test
    fun `모르는_표시성별_이름은_null이다`() {
        val odd = members(
            """{"members":[{"memberId":31,"displayName":"a","role":"MEMBER","shownGender":"OTHER"}]}"""
        ).single()

        assertNull(odd.shownGender)
    }
}
