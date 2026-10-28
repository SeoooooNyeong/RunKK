package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 안 랭킹(서버 crew.CrewRankingView, FR-137). */
data class CrewRanking(val entries: List<Entry>) {
    enum class Period {
        WEEKLY,
        MONTHLY
    }

    data class Entry(val rank: Int, val displayName: String, val distance: Distance)
}
