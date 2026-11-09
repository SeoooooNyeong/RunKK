package com.a207.runkk.app.api.core

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 요청에 접근 토큰을 붙인다(6.1.14). WebSocket 핸드셰이크도 같은 OkHttp를 쓰므로 헤더가 붙는다(design-review.md E1).
 * 이미 Authorization 헤더가 있는 요청(TokenAuthenticator의 재요청)은 그대로 둔다.
 */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bound = request.tag(BoundSession::class.java)
        if (bound != null && !bound.isCurrent(tokens)) throw IOException("Request session changed")
        val access = bound?.accessToken ?: tokens.accessToken()
        if (access == null || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, BEARER + access).build())
    }

    internal companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
    }
}
