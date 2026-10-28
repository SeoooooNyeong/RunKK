package com.a207.runkk.app.api.chat

import kotlinx.serialization.Serializable

/** POST /crews/{id}/chat/messages 요청(서버 ChatController.ChatPostRequest). clientMessageId는 UUID 36자(재전송 멱등, D-101). */
@Serializable
internal data class ChatPostRequest(val clientMessageId: String, val content: String)
