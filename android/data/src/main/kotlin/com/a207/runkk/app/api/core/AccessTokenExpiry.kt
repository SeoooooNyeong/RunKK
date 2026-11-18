package com.a207.runkk.app.api.core

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 접근 토큰(JWT, 서버 JwtTokenIssuer)의 만료 시각을 페이로드의 exp(초)에서 읽는다(Jira 326). 서명은 서버가 검증하므로 값만 읽는다.
 * JWT가 아니거나 exp가 없거나 숫자가 아니면 null이다(그때는 401 뒤 TokenAuthenticator가 갱신한다).
 */
object AccessTokenExpiry {
    fun of(token: String): Instant? {
        val payload = token.split('.').takeIf { it.size == JWT_PARTS }?.get(1) ?: return null
        return try {
            val json = Base64.getUrlDecoder().decode(payload).decodeToString()
            val exp = (Json.parseToJsonElement(json) as? JsonObject)?.get(EXP) as? JsonPrimitive ?: return null
            if (exp.isString) return null
            exp.longOrNull?.let(Instant::ofEpochSecond)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }

    private const val JWT_PARTS = 3
    private const val EXP = "exp"
}
