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
}

/** 회원 본인·프로필·설정·동의·약관·탈퇴 REST(5.7.1 MeController). */
internal interface MeApi {
    @GET("members/{id}/profile")
    suspend fun profile(@Path("id") memberId: Long): ProfileResponse

    @PATCH("me/profile")
    suspend fun editProfile(@Body body: ProfileEditRequest)

    @GET("me/settings")
    suspend fun settings(): MemberSettingsResponse

    @PATCH("me/settings")
    suspend fun changeSetting(@Body body: SettingChangeBody)

    @PUT("me/consents/{item}")
    suspend fun giveConsent(@Path("item") item: String)

    @DELETE("me/consents/{item}")
    suspend fun withdrawConsent(@Path("item") item: String)

    @GET("legal/{type}")
    suspend fun legalDocument(@Path("type") type: String): LegalDocumentResponse

    @DELETE("me")
    suspend fun withdraw()
}
