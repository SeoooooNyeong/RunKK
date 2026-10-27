package com.a207.runkk.app.api.account

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** 로그인·가입·로그아웃 REST(5.7.1 AuthController·SignUpController). 갱신(POST /auth/refresh)은 api.core.TokenAuthenticator가 부른다. */
internal interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @POST("members")
    suspend fun signUp(@Body body: SignUpRequest): TokenPairResponse

    /** 게스트 시작(공개, 201, FR-197). 게스트 자격 증명은 이 응답에서만 한 번 온다(D-89). */
    @POST("members/guest")
    suspend fun startAsGuest(@Body body: GuestStartRequest): GuestStartResponse

    /** 게스트의 소셜 계정 연동(FR-198). 경로는 /me 아래지만 서버 AuthController가 받는다(5.7.1). */
    @POST("me/social-account")
    suspend fun linkSocialAccount(@Body body: SocialLinkRequest): SocialLinkResponse
}
