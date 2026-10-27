package com.a207.runkk.app.ui.chat

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import javax.inject.Inject

/** 크루 채팅방 경로 Routes.CrewChat(crewId)를 그래프에 등록한다(6.1.14 ChatFeatureEntry). */
class ChatFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.CREW_CHAT

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.addEntryProvider(Routes.CrewChat::class) { key -> ChatRoomRoute(key) }
    }
}
