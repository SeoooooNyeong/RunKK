package com.a207.runkk.app.api.chat

import kotlinx.serialization.Serializable

/** GET /chat-messages/{id}/reactions 응답(서버 ChatController.ReactorListResponse, contract-W5 3.2). */
@Serializable
internal data class ReactorListResponse(val messageId: Long, val reactors: List<ReactorItem> = emptyList())
