package com.a207.runkk.app.api.core

import com.a207.runkk.app.api.core.AuthInterceptor.Companion.AUTHORIZATION
import com.a207.runkk.app.api.core.AuthInterceptor.Companion.BEARER
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 접근 토큰이 만료돼 401이 오면 갱신한다(6.1.14, design-review.md M36·M40). 갱신 호출은 SessionRefresher에 맡긴다(D-110):
 * POST /auth/refresh, 거부면 게스트 세션 재개 1회, 그래도 거부면 로그아웃. 일시 오류는 토큰을 지우지 않고 원 요청만 실패로 둔다.
 * 같은 때 401을 받은 요청이 여럿이어도 갱신은 한 번만 한다: 다른 요청이 이미 바꾼 토큰이 있으면 그 토큰으로 다시 보낸다.
 */
class TokenAuthenticator(private val tokens: TokenProvider, private val refresher: SessionRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // 갱신한 토큰으로 다시 보낸 요청도 401이면 더 하지 않는다(갱신 1회).
        if (response.priorResponse != null) return null
        val bound = response.request.tag(BoundSession::class.java)
        if (bound != null && !bound.isCurrent(tokens)) return null
        return retry(response.request)?.takeIf { bound?.isCurrent(tokens) != false }
    }

    private fun retry(request: Request): Request? {
        val sentAccess = request.header(AUTHORIZATION)?.removePrefix(BEARER)
        val current = tokens.accessToken()
        if (current != null && current != sentAccess) return request.withAccess(current)
        if (tokens.refreshToken() == null) return null
        return when (val renewal = refresher.refreshNow()) {
            is SessionRefresher.Refresh.Renewed -> request.withAccess(renewal.accessToken)
            SessionRefresher.Refresh.Rejected, SessionRefresher.Refresh.Unavailable -> null
        }
    }

    private fun Request.withAccess(access: String): Request =
        newBuilder().header(AUTHORIZATION, BEARER + access).build()
}
