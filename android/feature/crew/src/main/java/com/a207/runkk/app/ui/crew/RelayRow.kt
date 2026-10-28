package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance

/** 중계 한 줄(FR-149). 위치는 없다. [me]는 내 줄(강조해 보인다). */
data class RelayRow(
    val name: String,
    val distance: Distance,
    val rank: Int?,
    val connection: String,
    val isMe: Boolean = false
)
