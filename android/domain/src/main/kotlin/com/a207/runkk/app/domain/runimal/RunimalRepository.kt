package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 조회 포트(GET /runimal, GET /runimal/card, GET /runimal/types). 구현 api.runimal. */
interface RunimalRepository {
    suspend fun status(): Outcome<RunimalStatus>

    /** 유형이 없으면 Failure(Rejected(NO_TYPE))(FR-168). */
    suspend fun card(): Outcome<RunimalCard>

    /** 16유형과 유형마다 확정받은 적 있는지(FR-226, D-285). */
    suspend fun types(): Outcome<RunimalTypeBoard>

    /** 유형 하나의 상세(FR-226, D-285). 모르는 유형은 Rejected(BAD_REQUEST)다. */
    suspend fun type(type: String): Outcome<RunimalTypeDetail>
}
