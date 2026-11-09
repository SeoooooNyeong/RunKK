package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkStatusChipTone
import com.a207.runkk.app.domain.notification.Event

/**
 * 이벤트 카드 상태 칩(피그마 RunKK / 이벤트 / 상태 칩 62268:2820): 새 이벤트(보라), 참여 중(라임), 곧 열려요와 종료(회색).
 * 그리기는 디자인 시스템 RunkkStatusChip이 맡는다.
 */
enum class EventChip(@StringRes val labelRes: Int, val tone: RunkkStatusChipTone) {
    NEW(R.string.event_chip_new, RunkkStatusChipTone.Violet),
    ENTERED(R.string.event_chip_entered, RunkkStatusChipTone.Lime),
    UPCOMING(R.string.event_chip_upcoming, RunkkStatusChipTone.Muted),
    ENDED(R.string.event_chip_ended, RunkkStatusChipTone.Muted);

    companion object {
        fun of(event: Event): EventChip = when (event.status) {
            Event.Status.UPCOMING -> UPCOMING
            Event.Status.ENDED -> ENDED
            Event.Status.ONGOING -> if (event.isEntered) ENTERED else NEW
        }
    }
}
