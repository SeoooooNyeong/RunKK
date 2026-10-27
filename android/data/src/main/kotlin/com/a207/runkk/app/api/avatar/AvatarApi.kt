package com.a207.runkk.app.api.avatar

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/** 아바타·상점 REST(5.7.1 AvatarController). */
internal interface AvatarApi {
    @GET("avatar")
    suspend fun avatar(): AvatarResponse

    /** 셀카(JPEG, 긴 변 1024px·1MB 이하)를 multipart로 보낸다. 서버는 메모리로만 받는다(D-59). */
    @Multipart
    @POST("avatar/selfie")
    suspend fun createFromSelfie(@Part selfie: MultipartBody.Part): AvatarResponse

    @POST("avatar/default")
    suspend fun chooseDefault(@Body body: DefaultCharacterRequest): AvatarResponse

    @PUT("avatar/equipment")
    suspend fun equip(@Body body: EquipRequest): AvatarResponse

    @GET("shop")
    suspend fun shop(@Query("slot") slot: String?): ShopResponse

    @POST("shop/purchases")
    suspend fun purchase(@Body body: PurchaseRequest): PurchaseResponse

    /** 랜드마크 배경 목록(FR-201, FR-202). */
    @GET("avatar/backgrounds")
    suspend fun backgrounds(): BackgroundsResponse

    /** 배경 고르기(FR-202). key가 null이면 기본 배경, 해금하지 않은 키면 409 BACKGROUND_LOCKED. */
    @PUT("avatar/background")
    suspend fun chooseBackground(@Body body: BackgroundRequest): BackgroundsResponse
}
