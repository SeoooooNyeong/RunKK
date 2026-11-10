package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 만들기·정보 바꾸기 입력(FR-110, FR-112). */
data class CrewForm(
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: CrewVisibility,
    val joinPolicy: JoinPolicy,
    /** 로고 주소(POST /crew-logos가 준 /api/v1/crew-logos/{id}), 없으면 null(contract-W4 6·8절). */
    val logoUrl: String? = null
)
