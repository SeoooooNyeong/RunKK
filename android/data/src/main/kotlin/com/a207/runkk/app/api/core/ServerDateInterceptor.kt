package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.ServerClock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 서버 응답의 Date 머리글을 서버 시각 추정(ServerClock)에 넘긴다. 디스크 캐시 응답의 지난 Date를 읽지 않게 네트워크 인터셉터로 단다.
 * 머리글이 없거나 읽을 수 없으면 아무것도 하지 않는다.
 */
class ServerDateInterceptor(private val serverClock: ServerClock) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        response.headers.getDate(DATE)?.let { serverClock.onServerDate(it.toInstant()) }
        return response
    }

    private companion object {
        const val DATE = "Date"
    }
}
