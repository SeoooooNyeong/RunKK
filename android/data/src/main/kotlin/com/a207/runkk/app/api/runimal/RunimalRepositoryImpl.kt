package com.a207.runkk.app.api.runimal

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.domain.runimal.RunimalStatus
import javax.inject.Inject

/** Runimal 조회 포트를 REST로 구현한다(6.1.14 api.runimal). */
internal class RunimalRepositoryImpl
@Inject
constructor(private val api: RunimalApi) : RunimalRepository {
    override suspend fun status(): Outcome<RunimalStatus> = ApiErrorMapper.call { api.status().toDomain() }

    override suspend fun card(): Outcome<RunimalCard> = ApiErrorMapper.call { api.card().toDomain() }
}

internal fun RunimalCardResponse.toDomain(): RunimalCard = RunimalCard(type, title, description, axisRatios)

internal fun RunimalStatusResponse.toDomain(): RunimalStatus =
    RunimalStatus(type, provisional, remainingRuns, card?.toDomain(), allTypes)
