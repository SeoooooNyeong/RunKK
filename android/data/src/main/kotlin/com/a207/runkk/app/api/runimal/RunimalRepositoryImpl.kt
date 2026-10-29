package com.a207.runkk.app.api.runimal

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.domain.runimal.RunimalStatus
import com.a207.runkk.app.domain.runimal.RunimalTypeBoard
import com.a207.runkk.app.domain.runimal.RunimalTypeDetail
import javax.inject.Inject

/** Runimal 조회 포트를 REST로 구현한다(6.1.14 api.runimal). */
internal class RunimalRepositoryImpl
@Inject
constructor(private val api: RunimalApi) : RunimalRepository {
    override suspend fun status(): Outcome<RunimalStatus> = ApiErrorMapper.call { api.status().toDomain() }

    override suspend fun card(): Outcome<RunimalCard> = ApiErrorMapper.call { api.card().toDomain() }

    override suspend fun types(): Outcome<RunimalTypeBoard> = ApiErrorMapper.call { api.types().toDomain() }

    override suspend fun type(type: String): Outcome<RunimalTypeDetail> =
        ApiErrorMapper.call { api.type(type).toDomain() }
}

internal fun RunimalCardResponse.toDomain(): RunimalCard =
    RunimalCard(type, title, description, axisRatios, axes.mapNotNull { it.toDomain() })

/**
 * 축 막대의 기운 쪽은 서버 lean(A|B)으로 정한다. lean이 없는 옛 서버면 우세 성향 이름(PersonalityTag 이름)으로 대신 판정한다:
 * B쪽 이름(소셜런·장거리·탐험길·즉흥런)이면 B, A쪽 이름이면 A이고 모르는 이름은 버린다.
 */
private fun RunimalCardResponse.AxisDto.toDomain(): RunimalCard.AxisBar? {
    val side = enumByName<AxisLean>(lean) ?: when (label) {
        in SIDE_A_LABELS -> AxisLean.A
        in SIDE_B_LABELS -> AxisLean.B
        else -> return null
    }
    return RunimalCard.AxisBar(axis, side, percent)
}

/** 서버 PersonalityTag의 A쪽·B쪽 성향 이름(running.RunimalAxis sideA·sideB). 서버 값이라 api가 갖는다. */
private val SIDE_A_LABELS = setOf("솔로런", "단거리", "단골길", "루틴런")
private val SIDE_B_LABELS = setOf("소셜런", "장거리", "탐험길", "즉흥런")

internal fun RunimalStatusResponse.toDomain(): RunimalStatus =
    RunimalStatus(type, provisional, remainingRuns, card?.toDomain(), allTypes)

/** 모르는 축 값(A·B 밖)은 버린다. */
private fun Map<String, String>.toLeans(): Map<String, AxisLean> =
    mapNotNull { (axis, lean) -> enumByName<AxisLean>(lean)?.let { axis to it } }.toMap()

internal fun RunimalTypesResponse.toDomain(): RunimalTypeBoard = RunimalTypeBoard(
    current = current,
    types = types.map {
        RunimalTypeBoard.Entry(it.type, it.displayName, it.icon, it.description, it.axes.toLeans(), it.everConfirmed)
    }
)

internal fun RunimalTypeResponse.toDomain(): RunimalTypeDetail = RunimalTypeDetail(
    type = type,
    displayName = displayName,
    icon = icon,
    description = description,
    axes = axes.toLeans(),
    traits = traits,
    sharePercent = sharePercent,
    computedAt = computedAt?.let(ServerTime::parse)
)
