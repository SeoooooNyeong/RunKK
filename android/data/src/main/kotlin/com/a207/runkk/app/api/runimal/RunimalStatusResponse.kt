package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** 서버 runimal.RunimalStatusView. remainingRuns는 유형 확정까지 남은 유효 러닝 수. */
@Serializable
internal data class RunimalStatusResponse(
    val type: String? = null,
    val provisional: Boolean = false,
    val remainingRuns: Int? = null,
    val card: RunimalCardResponse? = null,
    val allTypes: List<String> = emptyList()
)
