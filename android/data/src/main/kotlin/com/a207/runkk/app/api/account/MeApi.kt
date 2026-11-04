package com.a207.runkk.app.api.account

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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

    /** 첫 사용 가이드를 봤는가(D-277, 서버 D-244). */
    @GET("me/guide")
    suspend fun guide(): GuideResponse

    /** 본인 배치고사 진행(FR-279~FR-282, 05-03 5.7.3.2 P1). */
    @GET("me/placement")
    suspend fun placement(): PlacementResponse

    /** 첫 사용 가이드를 끝냈다(204, 멱등). */
    @PUT("me/guide")
    suspend fun markGuideSeen()
}
