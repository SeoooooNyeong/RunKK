package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.ServerClock
import java.io.IOException
import java.time.Duration
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 요청에 접근 토큰을 붙인다(6.1.14). WebSocket 핸드셰이크도 같은 OkHttp를 쓰므로 헤더가 붙는다(design-review.md E1).
 * 이미 Authorization 헤더가 있는 요청(TokenAuthenticator의 재요청)은 그대로 둔다.
 *
 * 미리 갱신(Jira 326): [refresher]와 [clock]이 있으면 접근 토큰(JWT)의 exp − 서버 시각이 [AHEAD](60초)보다 짧을 때 원 요청 전에
 * [SessionRefresher.refreshNow]를 부른다(다른 요청이 갱신 중이면 그 결과를 기다린다). exp를 못 읽거나 갱신이 일시 오류로 실패하면
 * 막지 않고 지금 토큰으로 보낸다(401이면 기존 TokenAuthenticator 경로). 같은 토큰으로 미리 갱신이 실패했으면 그 토큰으로는 다시
 * 미리 갱신하지 않는다(일시 오류 중 요청마다 갱신을 보내지 않게). 세션에 묶인 요청(BoundSession)은 미리 갱신하지 않는다.
 */
class AuthInterceptor(
    private val tokens: TokenProvider,
    private val refresher: SessionRefresher? = null,
    private val clock: ServerClock? = null
) : Interceptor {
    /** 미리 갱신이 일시 오류로 실패한 접근 토큰. */
    @Volatile
    private var failedAhead: String? = null

    /** 미리 갱신을 한 번에 하나만 한다. */
    private val aheadLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bound = request.tag(BoundSession::class.java)
        if (bound != null && !bound.isCurrent(tokens)) throw IOException("Request session changed")
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val access = bound?.accessToken ?: refreshAhead(tokens.accessToken())
        if (access == null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, BEARER + access).build())
    }

    /** 만료가 가까우면 먼저 갱신하고 지금 쓸 접근 토큰을 돌려준다. */
    private fun refreshAhead(access: String?): String? {
        val refresher = refresher ?: return access
        val clock = clock ?: return access
        if (access == null || access == failedAhead) return access
        val expiry = AccessTokenExpiry.of(access) ?: return access
        if (Duration.between(clock.now(), expiry) >= AHEAD) return access
        // 함께 온 요청은 한 줄로 세운다: 앞 요청이 갱신을 마쳤으면 바뀐 토큰을 쓰고 다시 갱신하지 않는다.
        return synchronized(aheadLock) {
            val current = tokens.accessToken()
            when {
                current != access -> current
                access == failedAhead -> access
                else -> renew(refresher, access)
            }
        }
    }

    private fun renew(refresher: SessionRefresher, access: String): String? =
        when (val renewed = refresher.refreshNow()) {
            is SessionRefresher.Refresh.Renewed -> tokens.accessToken() ?: renewed.accessToken

            SessionRefresher.Refresh.Unavailable -> {
                // 다른 요청이 그사이 갱신했으면 새 토큰을 쓴다. 아니면 이 토큰으로는 다시 미리 갱신하지 않는다.
                val current = tokens.accessToken()
                if (current == access) failedAhead = access
                current
            }

            SessionRefresher.Refresh.Rejected -> tokens.accessToken()
        }

    internal companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "

        /** 만료까지 이보다 짧으면 미리 갱신한다. */
        val AHEAD: Duration = Duration.ofSeconds(60)
    }
}
