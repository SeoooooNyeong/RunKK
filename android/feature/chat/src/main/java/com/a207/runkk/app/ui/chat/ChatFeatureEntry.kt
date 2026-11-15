package com.a207.runkk.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalSlotCrewId
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import javax.inject.Inject

/**
 * 크루 채팅을 크루 홈의 세 번째 탭으로 넣는다(6.1.14 ChatFeatureEntry, D-653). 따로 뜨는 채팅방 경로는 없고, ui.crew 크루 홈이
 * 그리는 칸 SlotKey.CREW_CHAT_TAB에 ChatTabContent를 넣는다(크루 ID는 LocalSlotCrewId).
 */
class ChatFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.CREW_CHAT

    override fun register(builder: EntryProviderScope<NavKey>) = Unit

    override val slots: Map<SlotKey, @Composable () -> Unit> =
        mapOf(SlotKey.CREW_CHAT_TAB to { CrewChatSlotContent() })
}

/** 칸 안: 크루 홈이 넘긴 크루 ID가 있을 때만 채팅 탭을 그린다. */
@Composable
private fun CrewChatSlotContent() {
    val crewId = LocalSlotCrewId.current ?: return
    ChatTabContent(crewId = crewId, isVisible = true)
}
