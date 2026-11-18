package com.a207.runkk.app.api.core

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 접근 토큰(JWT, 서버 JwtTokenIssuer)의 만료 시각 읽기(Jira 326). 서명은 서버가 검증하므로 값만 읽는다. */
class AccessTokenExpiryTest {
    @Test
    fun `페이로드의 exp(초)를 시각으로 읽는다`() {
        assertEquals(
            Instant.ofEpochSecond(1_800_000_000),
            AccessTokenExpiry.of(jwt("""{"sub":"7","exp":1800000000}"""))
        )
    }

    @Test
    fun `exp가 없거나 숫자가 아니거나 형식이 틀리면 null`() {
        assertNull(AccessTokenExpiry.of(jwt("""{"sub":"7"}""")))
        assertNull(AccessTokenExpiry.of(jwt("""{"exp":"soon"}""")))
        assertNull(AccessTokenExpiry.of("not-a-jwt"))
        assertNull(AccessTokenExpiry.of("a.%%%.c"))
        assertNull(AccessTokenExpiry.of("a.${b64("[1,2]")}.c"))
    }

    companion object {
        fun b64(text: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

        /** 서명 없는 시험용 JWT(헤더.페이로드.서명). */
        fun jwt(payload: String): String = b64("""{"alg":"HS256"}""") + "." + b64(payload) + ".sig"
    }
}
