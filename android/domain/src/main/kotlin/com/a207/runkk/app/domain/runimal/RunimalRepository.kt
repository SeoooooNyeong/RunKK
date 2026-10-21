package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 조회 포트(GET /runimal, GET /runimal/card). 구현 api.runimal. */
interface RunimalRepository {
    suspend fun status(): Outcome<RunimalStatus>

    /** 유형이 없으면 Failure(Rejected(NO_TYPE))(FR-168). */
    suspend fun card(): Outcome<RunimalCard>
}
