package com.a207.runkk.app.api.runbody

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodySession
import java.time.YearMonth
import javax.inject.Inject

/** 런바디 결과 포트를 REST로 구현한다(D-453 api.runbody). 거부는 본문 code(RUNBODY_NOT_FOUND 등)로 Rejected가 된다. */
internal class RunBodyRepositoryImpl
@Inject
constructor(private val api: RunBodyApi) : RunBodyRepository {
    override suspend fun session(recordId: String): Outcome<RunBodySession> =
        ApiErrorMapper.call { api.session(recordId).toDomain() }

    override suspend fun month(month: YearMonth): Outcome<RunBodyMonth> =
        ApiErrorMapper.call { api.month(month.toString()).toDomain() }
}
