package com.a207.runkk.app.api.core

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 접근 토큰 갱신을 한 곳에서 한다(6.1.14 api.core, D-110, D-89, D-153, D-159).
 * TokenAuthenticator(REST 401)와 data.live(닫기 코드 4401)가 함께 부른다.
 *
 * [refreshNow]: POST /auth/refresh를 한 번 부른다. 서버가 갱신을 거부(400·401·403)하면 저장된 게스트 자격 증명이 있을 때
 * POST /auth/guest/session을 한 번 시도한다(갱신 토큰 14일이 지난 게스트가 계정을 잃지 않게). 재개도 거부되거나 자격 증명이 없으면
 * TokenProvider.clear()로 로그인 상태를 끝낸다. 연결 실패·서버 일시 오류(5xx·408·429)·해독 실패는 거부가 아니므로 토큰을 지우지 않고
 * Unavailable을 돌려준다(서버 장애로 로그아웃되지 않게, FR-001).
 *
 * 동시에 부른 쪽은 진행 중인 한 번의 결과를 함께 받는다: 기다리는 사이 다른 호출이 갱신을 끝냈으면 그 결과를 그대로 돌려준다.
 * 응답을 기다리는 사이 로그아웃(clear)이나 새 로그인(save)이 있었으면 받은 토큰을 저장하지 않는다(로그아웃이 되살아나거나 새 로그인의
 * 토큰을 덮어쓰지 않게, AN5-C-8·AN6-2). 이때 결과는 Rejected지만 토큰은 지우지 않는다. 요청 전에 읽은 로그인 세대로
 * TokenProvider.updateInSession이 확인과 저장을 한 번에 한다.
 * 블로킹 연산이라 OkHttp 작업 스레드(TokenAuthenticator) 또는 IO 디스패처(data.live)에서 부른다.
 */
class SessionRefresher(
    private val tokens: TokenProvider,
    /** 인증 없는 클라이언트(갱신 요청에 만료된 접근 토큰을 싣지 않는다). 그래프의 인증된 클라이언트가 이 위에 얹힌다. */
    internal val client: OkHttpClient,
    private val refreshUrl: HttpUrl,
    private val json: Json
) {
    private val guestSessionUrl: HttpUrl = requireNotNull(refreshUrl.resolve(GUEST_SESSION_PATH))
    private val lock = Any()

    @Volatile
    private var generation = 0L

    /** 마지막 갱신 결과. lock 안에서만 쓴다. */
    private var last: Refresh = Refresh.Unavailable

    /** 갱신 결과. */
    sealed interface Refresh {
        /** 새 접근 토큰을 받아 TokenProvider에 저장했다. */
        data class Renewed(val accessToken: String) : Refresh {
            override fun toString(): String = "Renewed(***)"
        }

        /** 서버가 갱신·게스트 재개를 거부했다(또는 갱신 토큰이 없다). 로그인 상태가 끝났다. */
        data object Rejected : Refresh

        /** 연결 실패·일시 오류. 토큰은 그대로이고 나중에 다시 시도한다. */
        data object Unavailable : Refresh
    }

    fun refreshNow(): Refresh {
        val seen = generation
        synchronized(lock) {
            if (generation != seen) return last
            val result = renew()
            last = result
            generation = seen + 1
            return result
        }
    }

    private fun renew(): Refresh {
        val session = tokens.sessionGeneration()
        val refresh = tokens.refreshToken() ?: return Refresh.Rejected
        return when (
            val issued = post(
                refreshUrl,
                json.encodeToString(RefreshBody.serializer(), RefreshBody(refresh))
            )
        ) {
            is Reply.Issued -> store(session, issued)
            Reply.Unavailable -> Refresh.Unavailable
            Reply.Rejected -> resumeGuest(session)
        }
    }

    /** 갱신이 거부됐다: 게스트 자격 증명이 있으면 한 번만 재개를 시도한다. 거부만 로그아웃이다(D-159 (4)). */
    private fun resumeGuest(session: Long): Refresh {
        val credential = tokens.guestCredential() ?: return endSession()
        val body = json.encodeToString(GuestSessionBody.serializer(), GuestSessionBody(credential))
        return when (val resumed = post(guestSessionUrl, body)) {
            is Reply.Issued -> store(session, resumed)
            Reply.Unavailable -> Refresh.Unavailable
            Reply.Rejected -> endSession()
        }
    }

    /** 요청을 보낸 뒤 세션이 바뀌지 않았을 때만(로그아웃·새 로그인 없음) 저장한다(확인과 저장은 TokenProvider가 한 번에). 바뀌었으면 거부로 본다. */
    private fun store(session: Long, issued: Reply.Issued): Refresh =
        if (tokens.updateInSession(session, issued.accessToken, issued.refreshToken)) {
            Refresh.Renewed(issued.accessToken)
        } else {
            Refresh.Rejected
        }

    private fun endSession(): Refresh {
        // 코루틴 밖(OkHttp 작업 스레드·IO 스레드)에서 불리므로 지우기가 끝날 때까지 기다린다.
        runBlocking { tokens.clear() }
        return Refresh.Rejected
    }

    private fun post(url: HttpUrl, body: String): Reply {
        val request = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA_TYPE)).build()
        return try {
            client.newCall(request).execute().use { reply ->
                when {
                    reply.isSuccessful -> {
                        val pair = json.decodeFromString(TokenPairBody.serializer(), reply.body.string())
                        Reply.Issued(pair.accessToken, pair.refreshToken)
                    }

                    reply.code in REJECTED_STATUS -> Reply.Rejected

                    else -> Reply.Unavailable
                }
            }
        } catch (e: IOException) {
            Reply.Unavailable
        } catch (e: SerializationException) {
            Reply.Unavailable
        } catch (e: IllegalArgumentException) {
            Reply.Unavailable
        }
    }

    private sealed interface Reply {
        class Issued(val accessToken: String, val refreshToken: String) : Reply

        data object Rejected : Reply

        data object Unavailable : Reply
    }

    @Serializable
    private data class RefreshBody(val refreshToken: String)

    @Serializable
    private data class GuestSessionBody(val guestCredential: String)

    @Serializable
    private data class TokenPairBody(val accessToken: String, val refreshToken: String)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** POST /auth/refresh 옆의 POST /auth/guest/session. */
        const val GUEST_SESSION_PATH = "guest/session"

        /** 서버가 받아들이지 않음(만료·폐기·형식 오류). */
        val REJECTED_STATUS = setOf(400, 401, 403)
    }
}
