package com.a207.runkk.app.api.core

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 일반 API 디스크 캐시(Jira 328)를 계정별 칸으로 나눈다(S15P31A207-345 2차, 받아 둔 것은 지우지 않는다). 요청마다 로그인한 회원
 * ([member], 접근 토큰 sub)을 보고 캐시 열쇠 주소에 그 회원 칸 표식을 붙인다(OkHttp cacheUrlOverride, 서버로 가는 주소는 그대로).
 * OkHttp 캐시는 주소로만 찾으므로 다른 계정은 앞 계정의 칸을 찾지 못하고(앞 계정 응답이 나가지 않는다), 앞 계정으로 돌아오면 그 칸이
 * 그대로 남아 다시 받지 않는다. 계정이 바뀌어도 캐시를 비우지 않는다(오래된 것은 OkHttp가 크기 상한으로 밀어낸다).
 *
 * 칸은 요청을 보낼 때의 회원으로 정해지므로, 요청이 도는 사이 회원이 바뀌어 늦게 도착한 응답도 보낸 회원의 칸에만 남는다. 로그아웃
 * 상태의 요청은 로그아웃 칸을 쓴다. OkHttp는 GET만 캐시하므로 GET에만 붙인다. 애플리케이션 인터셉터로 붙여 캐시 조회보다 먼저 돈다.
 * 공용 그림(다른 러너 머리, FaceImageSource)은 계정과 상관없이 같이 쓰므로 그 클라이언트에는 붙이지 않는다.
 */
internal class ApiCacheGuard(private val member: () -> Long?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != GET) return chain.proceed(request)
        val slot = member()?.toString() ?: LOGGED_OUT
        val key = request.url.newBuilder().addQueryParameter(SLOT_PARAM, slot).build()
        return chain.proceed(request.newBuilder().cacheUrlOverride(key).build())
    }

    private companion object {
        const val GET = "GET"
        const val LOGGED_OUT = "-"

        /** 캐시 열쇠에만 붙는 칸 표식(서버로 보내지 않는다). */
        const val SLOT_PARAM = "runkk-cache-slot"
    }
}
