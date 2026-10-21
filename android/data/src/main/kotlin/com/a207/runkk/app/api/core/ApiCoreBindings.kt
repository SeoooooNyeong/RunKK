package com.a207.runkk.app.api.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 서버 연결을 그래프에 내준다(5.1.2 api.core). 서버 주소는 플레이버별 BuildConfig.API_BASE_URL이다.
 * 그래프의 OkHttpClient는 인증된 것 하나(AuthInterceptor + TokenAuthenticator)이고, 갱신용 인증 없는 클라이언트는 밖에 내지 않는다.
 * 요청·응답 본문 로깅은 하지 않는다(토큰·위치가 로그에 남지 않게).
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiCoreBindings {
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttpClient(tokens: TokenProvider, json: Json): OkHttpClient {
        val base =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        val refreshUrl = requireNotNull(BuildConfig.API_BASE_URL.toHttpUrl().resolve("auth/refresh"))
        return authenticatedClient(base, tokens, refreshUrl, json)
    }

    /** 인증 없는 base 위에 접근 토큰 부착과 401 갱신을 얹는다. 갱신 요청은 base로 보낸다(시험도 이 조립을 쓴다). */
    internal fun authenticatedClient(
        base: OkHttpClient,
        tokens: TokenProvider,
        refreshUrl: HttpUrl,
        json: Json
    ): OkHttpClient = base
        .newBuilder()
        .addInterceptor(AuthInterceptor(tokens))
        .authenticator(TokenAuthenticator(tokens, base, refreshUrl, json))
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit
        .Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
