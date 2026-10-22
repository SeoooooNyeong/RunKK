package com.a207.runkk.app.api.core

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 로그인한 본인의 회원 ID를 접근 토큰에서 읽는다. 서버가 JWT의 sub에 회원 ID를 싣는다(JwtTokenIssuer).
 * 도메인 포트의 SELF(=0)를 받은 구현이 실제 ID로 바꿀 때 쓴다. 서명은 서버가 검증하므로 여기서는 값만 읽고,
 * 토큰이 없거나 형식이 틀리면 null이다(호출한 쪽이 Unauthorized로 본다).
 */
@Singleton
internal class SelfMember
@Inject
constructor(private val tokens: TokenProvider) {
    fun memberId(): Long? = tokens.accessToken()?.let(::subjectOf)

    /** requested가 self(본인 표시)면 본인 ID로, 아니면 그대로. 본인 ID를 모르면 null. */
    fun resolve(requested: Long, self: Long): Long? = if (requested == self) memberId() else requested

    private fun subjectOf(jwt: String): Long? {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        return try {
            val json = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
    }
}
