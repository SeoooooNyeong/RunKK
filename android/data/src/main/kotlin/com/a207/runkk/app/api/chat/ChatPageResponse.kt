package com.a207.runkk.app.api.chat

import kotlinx.serialization.Serializable

/** GET /crews/{id}/chat/messages 응답(서버 ChatController.ChatPageResponse). */
@Serializable
internal data class ChatPageResponse(val messages: List<ChatMessageItem> = emptyList(), val hasMore: Boolean = false)
