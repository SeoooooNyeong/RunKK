package com.a207.runkk.app.api.core

import java.io.File
import java.io.IOException
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 일반 API 디스크 캐시(Jira 328)를 회원 것으로 지킨다. 요청마다 로그인한 회원([member], 접근 토큰 sub)을 보고, 캐시를 채운 회원과
 * 다르면(계정 바뀜·로그아웃) 캐시를 비운 뒤에 보낸다. OkHttp 캐시는 주소로만 찾으므로 다른 계정에 앞 계정의 응답이 나가지 않게 한다.
 * 캐시를 채운 회원은 [ownerFile]에 남겨 앱을 새로 켜도 같은 회원이면 비우지 않는다(크루 로고를 재시작마다 다시 받지 않게).
 * 요청이 도는 사이 회원이 바뀌면(앞 회원 응답이 늦게 도착해 캐시에 남을 수 있다) 다음 요청이 다시 비운다.
 * 애플리케이션 인터셉터로 붙여 캐시 조회보다 먼저 돈다. 여러 스레드가 함께 써도 된다.
 */
internal class ApiCacheGuard(private val cache: Cache, private val ownerFile: File, private val member: () -> Long?) :
    Interceptor {
    private val lock = Any()
    private var isLoaded = false
    private var owner: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val asked = member()?.toString() ?: LOGGED_OUT
        guard(asked)
        val response = chain.proceed(chain.request())
        // 요청이 도는 사이 회원이 바뀌었으면 이 응답은 앞 회원 것이다. 다음 요청이 캐시를 다시 비우게 한다
        if ((member()?.toString() ?: LOGGED_OUT) != asked) synchronized(lock) { owner = null }
        return response
    }

    private fun guard(current: String) = synchronized(lock) {
        if (!isLoaded) {
            isLoaded = true
            owner = try {
                ownerFile.takeIf { it.isFile }?.readText()
            } catch (e: IOException) {
                null
            }
        }
        if (owner == current) return@synchronized
        try {
            cache.evictAll()
            ownerFile.parentFile?.mkdirs()
            ownerFile.writeText(current)
        } catch (e: IOException) {
            // 비우지 못하면 서버 Cache-Control만 따른다(다음 요청이 다시 비운다).
            return@synchronized
        }
        owner = current
    }

    private companion object {
        const val LOGGED_OUT = "-"
    }
}
