package com.a207.runkk.app.api.core

import com.a207.runkk.app.api.core.AuthInterceptor.Companion.AUTHORIZATION
import com.a207.runkk.app.api.core.AuthInterceptor.Companion.BEARER
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * 접근 토큰이 만료돼 401이 오면 갱신한다(6.1.14, design-review.md M36·M40).
 * Retrofit 없이 OkHttp(인증 없는 refreshClient)로 POST /auth/refresh를 한 번 부르고 TokenProvider.update 뒤 원 요청을 다시 보낸다.
 * 서버가 갱신을 거부하면 TokenProvider.clear()를 불러 로그인 상태를 끝낸다. 네트워크가 끊겨 갱신하지 못한 것은 거부가 아니므로
 * 토큰을 지우지 않는다(다음 요청에서 다시 갱신한다).
 * 같은 때 401을 받은 요청이 여럿이어도 갱신은 한 번만 한다: 다른 요청이 이미 바꾼 토큰이 있으면 그 토큰으로 다시 보낸다.
 */
class TokenAuthenticator(
    private val tokens: TokenProvider,
    private val refreshClient: OkHttpClient,
    private val refreshUrl: HttpUrl,
    private val json: Json
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 갱신한 토큰으로 다시 보낸 요청도 401이면 더 하지 않는다(갱신 1회).
        if (response.priorResponse != null) return null
        val sentAccess = response.request.header(AUTHORIZATION)?.removePrefix(BEARER)
        synchronized(lock) {
            val current = tokens.accessToken()
            if (current != null && current != sentAccess) return response.request.withAccess(current)
            val refresh = tokens.refreshToken() ?: return null
            val renewed =
                try {
                    requestRefresh(refresh)
                } catch (e: IOException) {
                    return null
                }
            if (renewed == null) {
                // OkHttp 작업 스레드에서 불리므로(코루틴 밖) 지우기가 끝날 때까지 기다린다.
                runBlocking { tokens.clear() }
                return null
            }
            tokens.update(renewed.accessToken, renewed.refreshToken)
            return response.request.withAccess(renewed.accessToken)
        }
    }

    /** 갱신 응답. 서버가 거부했거나(2xx 아님) 본문을 읽을 수 없으면 null, 연결 실패는 IOException. */
    private fun requestRefresh(refreshToken: String): TokenPairBody? {
        val body = json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken))
        val request =
            Request
                .Builder()
                .url(refreshUrl)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        refreshClient.newCall(request).execute().use { reply ->
            if (!reply.isSuccessful) return null
            return try {
                json.decodeFromString(TokenPairBody.serializer(), reply.body.string())
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    private fun Request.withAccess(access: String): Request =
        newBuilder().header(AUTHORIZATION, BEARER + access).build()

    @Serializable
    private data class RefreshBody(val refreshToken: String)

    @Serializable
    private data class TokenPairBody(val accessToken: String, val refreshToken: String)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
