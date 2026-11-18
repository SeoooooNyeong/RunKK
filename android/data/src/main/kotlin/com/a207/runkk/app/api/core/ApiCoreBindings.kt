package com.a207.runkk.app.api.core

import android.content.Context
import com.a207.runkk.app.core.model.ServerClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 서버 연결을 그래프에 내준다(5.1.2 api.core). 서버 주소는 플레이버별 BuildConfig.API_BASE_URL이다.
 * 그래프의 OkHttpClient는 인증된 것 하나(AuthInterceptor + TokenAuthenticator)이고, 갱신용 인증 없는 클라이언트는 밖에 내지 않는다.
 * 모든 응답의 Date 머리글로 서버 시각 추정(ServerClock)을 맞춘다(ServerDateInterceptor).
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

    /** 서버 시각 추정 하나(앱 전체 공유). 기기 시계는 data.device.DeviceBindings가 내준다. */
    @Provides
    @Singleton
    fun serverClock(device: Clock): ServerClock = ServerClock(device)

    /** 갱신 담당 하나(TokenAuthenticator와 data.live가 함께 쓴다, D-110). 인증 없는 기본 클라이언트를 함께 갖는다. */
    @Provides
    @Singleton
    fun sessionRefresher(tokens: TokenProvider, json: Json, serverClock: ServerClock): SessionRefresher {
        val base =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addNetworkInterceptor(ServerDateInterceptor(serverClock))
                .build()
        val refreshUrl = requireNotNull(BuildConfig.API_BASE_URL.toHttpUrl().resolve("auth/refresh"))
        return SessionRefresher(tokens, base, refreshUrl, json)
    }

    @Provides
    @Singleton
    fun okHttpClient(tokens: TokenProvider, refresher: SessionRefresher, serverClock: ServerClock): OkHttpClient =
        authenticatedClient(refresher.client, tokens, refresher, serverClock)

    /** 인증 없는 base 위에 접근 토큰 부착과 401 갱신을 얹는다. 갱신 요청은 base로 보낸다(시험도 이 조립을 쓴다). */
    internal fun authenticatedClient(
        base: OkHttpClient,
        tokens: TokenProvider,
        refreshUrl: HttpUrl,
        json: Json
    ): OkHttpClient = authenticatedClient(base, tokens, SessionRefresher(tokens, base, refreshUrl, json))

    /** [clock]을 주면 접근 토큰 만료 60초 전에 원 요청보다 먼저 갱신한다(Jira 326). */
    internal fun authenticatedClient(
        base: OkHttpClient,
        tokens: TokenProvider,
        refresher: SessionRefresher,
        clock: ServerClock? = null
    ): OkHttpClient = base
        .newBuilder()
        .addInterceptor(AuthInterceptor(tokens, refresher.takeIf { clock != null }, clock))
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.tag(BoundSession::class.java)?.isCurrent(tokens) == false) {
                throw IOException("Request session changed")
            }
            chain.proceed(request)
        }
        .authenticator(TokenAuthenticator(tokens, refresher))
        .build()

    /**
     * 아바타 얼굴 그림. 인증된 클라이언트에 디스크 캐시(64MiB, 서버 Cache-Control을 따른다)를 얹는다. 셀카 머리 층(S15P31A207-300)은
     * 서버가 immutable로 주어 앱을 다시 켜도 여기서 준다: 내 머리 1024 층 여섯 장 약 4~5MB, 다른 러너 얼굴 원 512 층 세 장 수백 KB.
     * 같은 연결 풀을 쓰고 인증 헤더·토큰 갱신은 그대로다. 내 얼굴의 마지막 모습은 앱 전용 저장소(files/my-face)에 남긴다.
     */
    @Provides
    @Singleton
    internal fun faceImageSource(
        client: OkHttpClient,
        self: SelfMember,
        @ApplicationContext context: Context
    ): FaceImageSource = FaceImageSource(
        client.newBuilder().cache(Cache(File(context.cacheDir, FACE_CACHE_DIR), FACE_CACHE_BYTES)).build(),
        BuildConfig.API_BASE_URL.toHttpUrl(),
        selfId = self::memberId,
        selfStore = SelfFaceStore(File(context.filesDir, SELF_FACE_DIR))
    )

    private const val FACE_CACHE_DIR = "avatar-face"
    private const val SELF_FACE_DIR = "my-face"
    private const val FACE_CACHE_BYTES = 64L * 1024 * 1024

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit
        .Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
