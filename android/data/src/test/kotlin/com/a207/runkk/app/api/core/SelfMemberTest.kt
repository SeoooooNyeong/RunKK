package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.Outcome
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 본인 회원 ID는 접근 토큰(JWT)의 sub에서 읽는다(서버 JwtTokenIssuer가 sub에 회원 ID를 싣는다). */
class SelfMemberTest {
    @Test
    fun `접근 토큰의 sub를 회원 ID로 읽는다`() {
        assertEquals(42L, SelfMember(TokensOf(jwtOf("42"))).memberId())
    }

    @Test
    fun `토큰이 없거나 형식이 틀리면 null`() {
        assertNull(SelfMember(TokensOf(null)).memberId())
        assertNull(SelfMember(TokensOf("not-a-jwt")).memberId())
        assertNull(SelfMember(TokensOf("a.!!!.c")).memberId())
        assertNull(SelfMember(TokensOf(jwtOf("abc"))).memberId())
        assertNull(SelfMember(TokensOf(jwtOf("0"))).memberId())
        assertNull(SelfMember(TokensOf(jwtWithPayload("""{"iss":"runkk"}"""))).memberId())
    }

    @Test
    fun `SELF만 본인 ID로 바꾸고 다른 ID는 그대로 둔다`() {
        val self = SelfMember(TokensOf(jwtOf("42")))

        assertEquals(42L, self.resolve(0L, 0L))
        assertEquals(7L, self.resolve(7L, 0L))
        assertNull(SelfMember(TokensOf(null)).resolve(0L, 0L))
        assertEquals(7L, SelfMember(TokensOf(null)).resolve(7L, 0L))
    }

    private class TokensOf(private val access: String?) : TokenProvider {
        override fun accessToken(): String? = access

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

/** 시험용 JWT(서명은 검증하지 않는다). payload는 URL-safe Base64, 패딩 없음. */
internal fun jwtWithPayload(payloadJson: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
    return "$header.${encoder.encodeToString(payloadJson.toByteArray())}.sig"
}

internal fun jwtOf(memberId: String): String = jwtWithPayload("""{"sub":"$memberId","type":"ACCESS"}""")
