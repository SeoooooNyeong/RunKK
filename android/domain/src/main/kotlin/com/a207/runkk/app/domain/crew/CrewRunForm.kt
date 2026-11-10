package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 크루런 모집글 입력(FR-132, 피그마 F-12). [distance]가 null이면 제한 없음이다. 일시는 오늘~14일 뒤, 5분 단위만 서버가 받는다
 * (계약 contract-W6.md R3).
 */
data class CrewRunForm(
    val title: String,
    val body: String?,
    val distance: Distance?,
    val meetAt: Instant,
    val repeat: CrewRunRepeat = CrewRunRepeat.NONE
)
