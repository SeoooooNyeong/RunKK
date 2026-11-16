package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.navigation.CrewRunReturnReceipt

/** 크루런 결과 화면의 이동. */
sealed interface CrewRunResultEvent {
    /** 「확인」 뒤 나간다. [receipt]는 내 기록이 서버에 저장됐을 때만 있다(설계 05-32 CrewRunReturnReceipt). */
    data class Leave(val receipt: CrewRunReturnReceipt?) : CrewRunResultEvent
}
