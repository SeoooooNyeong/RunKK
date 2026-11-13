package com.a207.runkk.app.ui.record.coach

class CoachChatActions(
    val onBack: () -> Unit = {},
    val onSend: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onNewConversation: () -> Unit = {},
    val onOpenConversation: (String) -> Unit = {},
    val onConsents: () -> Unit = {},
    val onRefreshConversations: () -> Unit = {},
    val onReloadHistory: () -> Unit = {}
)
