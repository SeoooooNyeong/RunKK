package com.a207.runkk.app.api.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 반응한 사람 한 줄(서버 ChatController.Reactor, contract-W5 3.3). 서버 JSON 키 me는 Kotlin에서 isMe다. */
@Serializable
internal data class ReactorItem(
    val memberId: Long,
    val displayName: String,
    val kind: String,
    val reactedAt: String,
    @SerialName("me") val isMe: Boolean = false,
    val faceKey: String? = null,
    val shownGender: String? = null
)
