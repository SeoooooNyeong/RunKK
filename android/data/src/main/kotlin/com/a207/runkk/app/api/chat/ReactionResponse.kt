package com.a207.runkk.app.api.chat

import kotlinx.serialization.Serializable

/** PUT·DELETE /chat-messages/{id}/reactions/{kind} 응답(서버 ChatController.ReactionResponse). */
@Serializable
internal data class ReactionResponse(
    val messageId: Long,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet()
)
