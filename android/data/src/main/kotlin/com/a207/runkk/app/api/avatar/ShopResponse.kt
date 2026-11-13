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

@Serializable
internal data class ShopResponse(val items: List<Item> = emptyList(), val balance: Int = 0) {
    @Serializable
    data class Item(
        val itemId: Long,
        val slot: String,
        val name: String,
        val price: Int,
        val owned: Boolean = false,
        val onSale: Boolean = true,
        /** 그림 키(W8). */
        val assetKey: String? = null,
        /** 해금 조건을 못 채워 살 수 없음(W8). */
        val locked: Boolean = false,
        /** 해금 조건 문구(W8). */
        val unlockCondition: String? = null
    )
}
