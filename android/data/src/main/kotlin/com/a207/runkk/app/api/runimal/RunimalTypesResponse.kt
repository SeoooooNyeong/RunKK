package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable

/**
 * 16유형 목록(서버 RunimalController.TypesResponse, D-285). axes는 축 이름 → A·B, everConfirmed는 이 러너가 그 유형을
 * 한 번이라도 확정받았는가다.
 */
@Serializable
internal data class RunimalTypesResponse(val current: String? = null, val types: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(
        val type: String,
        val displayName: String = "",
        val icon: String? = null,
        val description: String = "",
        val axes: Map<String, String> = emptyMap(),
        val everConfirmed: Boolean = false
    )
}
