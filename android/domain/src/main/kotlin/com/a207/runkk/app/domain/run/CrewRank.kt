package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.util.UUID

/** 크루런 순위(순위, 인원). */
data class CrewRank(val rank: Int, val headcount: Int) {
    init {
        require(rank in 1..headcount) { "rank must be in 1..headcount: $rank / $headcount" }
    }
}
