package com.a207.runkk.app.domain.chat

/** 채팅 메시지 한 쪽(늘 오래된 → 새 순, FR-209). [hasMore]는 요청한 방향으로 더 있는가다. */
data class ChatPage(val messages: List<ChatMessage>, val hasMore: Boolean) {
    companion object {
        val EMPTY = ChatPage(emptyList(), hasMore = false)
    }
}
