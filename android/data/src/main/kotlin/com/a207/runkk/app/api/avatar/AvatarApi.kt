package com.a207.runkk.app.api.avatar

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** 아바타·상점 REST(5.7.1 AvatarController). */
internal interface AvatarApi {
    @GET("avatar")
    suspend fun avatar(): AvatarResponse

    /**
     * 셀카(JPEG, 긴 변 1024px·1MB 이하)를 multipart로 보낸다. 서버는 메모리로만 받고 얼굴 생성을 기다리지 않고 202와 작업을 돌려준다
     * (D-59, S15P31A207-276).
     */
    @Multipart
    @POST("avatar/selfie/jobs")
    suspend fun submitSelfie(@Part selfie: MultipartBody.Part): SelfieJobResponse

    /** 내 진행 중 셀카 작업(접수됐고 결과를 아직 반영하지 않은 것). 없으면 204라 본문이 null이다. */
    @GET("avatar/selfie/jobs/current")
    suspend fun currentSelfieJob(): Response<SelfieJobResponse>

    @GET("avatar/selfie/jobs/{jobId}")
    suspend fun selfieJob(@Path("jobId") jobId: String): SelfieJobResponse

    /**
     * 끝난 작업의 결과. 서버는 응답을 쓰기 전에 내 얼굴을 바꾸므로 앱은 본문(얼굴 층 PNG Data URL, 수 MB)을 읽지 않고 닫는다.
     * 그림은 FaceImageSource가 GET /avatar/heads/…로 따로 받는다.
     */
    @Streaming
    @GET("avatar/selfie/jobs/{jobId}/result")
    suspend fun selfieResult(@Path("jobId") jobId: String): ResponseBody

    @POST("avatar/default")
    suspend fun chooseDefault(@Body body: DefaultCharacterRequest): AvatarResponse

    @PUT("avatar/equipment")
    suspend fun equip(@Body body: EquipRequest): AvatarResponse

    @GET("shop")
    suspend fun shop(@Query("slot") slot: String?): ShopResponse

    @POST("shop/purchases")
    suspend fun purchase(@Body body: PurchaseRequest): PurchaseResponse

    /** 내 러너(W8 H-2, contract-W8.md 2.1). */
    @GET("avatar/runner")
    suspend fun runner(): RunnerResponse

    /** 랜드마크 배경 목록(FR-201, FR-202). */
    @GET("avatar/backgrounds")
    suspend fun backgrounds(): BackgroundsResponse

    /** 배경 고르기(FR-202). key가 null이면 기본 배경, 해금하지 않은 키면 409 BACKGROUND_LOCKED. */
    @PUT("avatar/background")
    suspend fun chooseBackground(@Body body: BackgroundRequest): BackgroundsResponse
}
