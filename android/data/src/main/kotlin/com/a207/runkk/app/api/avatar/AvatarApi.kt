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
}

/** 서버 avatar.AvatarView. equipped·rejected 키는 착용 부위(ItemSlot) 이름. */
@Serializable
internal data class AvatarResponse(
    val faceKey: String? = null,
    val defaultCharacterId: String? = null,
    val bodyShape: String,
    val skinTone: String,
    val equipped: Map<String, Long> = emptyMap(),
    val balance: Int = 0,
    val canCreateSelfieToday: Boolean = false,
    val rejected: Map<String, String> = emptyMap()
)

@Serializable
internal data class DefaultCharacterRequest(val characterId: String)

/** 부위 이름 → 아이템 ID(null이면 해제). */
@Serializable
internal data class EquipRequest(val changes: Map<String, Long?>)

@Serializable
internal data class ShopResponse(val items: List<Item> = emptyList(), val balance: Int = 0) {
    @Serializable
    data class Item(
        val itemId: Long,
        val slot: String,
        val name: String,
        val price: Int,
        val owned: Boolean = false,
        val onSale: Boolean = true
    )
}

@Serializable
internal data class PurchaseRequest(val itemId: Long)

/** 구매 뒤 리워드 잔액. */
@Serializable
internal data class PurchaseResponse(val balance: Int)
