package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes
import com.a207.runkk.app.domain.notification.Event

/** 이벤트 카드 상태 칩(피그마 이벤트 모아보기): 새 이벤트, 참여 중, 곧 열려요, 종료. */
enum class EventChip(@StringRes val labelRes: Int) {
    NEW(R.string.event_chip_new),
    ENTERED(R.string.event_chip_entered),
    UPCOMING(R.string.event_chip_upcoming),
    ENDED(R.string.event_chip_ended);

    companion object {
        fun of(event: Event): EventChip = when (event.status) {
            Event.Status.UPCOMING -> UPCOMING
            Event.Status.ENDED -> ENDED
            Event.Status.ONGOING -> if (event.isEntered) ENTERED else NEW
        }
    }
}
