package com.a207.runkk.app.domain.crew

import java.time.LocalDate

/**
 * 공동 목표 축하 팝업을 그 주에 이미 보였는지 기기에 남긴다(D-655, 피그마 F-08b). 그 주 첫 진입 때 한 번만 자동으로 띄운다.
 * 서버에 보내는 상태가 없어 기기를 바꾸면 한 번 더 뜬다. 구현 data PreferencesCrewCelebrationStore.
 */
interface CrewCelebrationStore {
    fun isSeen(crewId: Long, weekStart: LocalDate): Boolean

    fun markSeen(crewId: Long, weekStart: LocalDate)

    companion object {
        /** 기기에 남기지 않는 메모리 구현(시험·미리보기용). */
        fun inMemory(): CrewCelebrationStore = object : CrewCelebrationStore {
            private val seen = mutableSetOf<Pair<Long, LocalDate>>()

            override fun isSeen(crewId: Long, weekStart: LocalDate): Boolean = (crewId to weekStart) in seen

            override fun markSeen(crewId: Long, weekStart: LocalDate) {
                seen += crewId to weekStart
            }
        }
    }
}
