package com.a207.runkk.app.ui.runimal

import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.domain.runimal.RunimalStatus
import com.a207.runkk.app.domain.runimal.RunimalTypeBoard
import com.a207.runkk.app.domain.runimal.RunimalTypeDetail
import java.time.Instant

/** 서버 유형 이름 → 한국어 이름(서버 displayName 흉내). */
internal val displayNames = mapOf(
    "MORNING_SPARROW" to "아침 참새",
    "WHEEL_HAMSTER" to "쳇바퀴 햄스터",
    "ALLEY_SQUIRREL" to "골목 탐험 다람쥐",
    "STROLLING_CAT" to "마실 나온 고양이",
    "COMMUTER_TURTLE" to "출근도장 거북이",
    "STEADY_CAMEL" to "묵묵한 낙타",
    "SKY_EAGLE" to "하늘길 독수리",
    "LONE_WOLF" to "고독한 늑대",
    "WALKING_PUPPY" to "산책 나온 강아지",
    "PLAYGROUND_MONKEY" to "놀이터 원숭이",
    "EXPLORER_OTTER" to "탐험대 수달",
    "PICNIC_RABBIT" to "소풍 나온 토끼",
    "PILLAR_ELEPHANT" to "크루의 기둥 코끼리",
    "PENGUIN_SQUAD" to "펭귄 군단",
    "LEADER_BIRD" to "V자 리더 철새",
    "WILD_HORSE" to "들판의 야생마"
)

/** 출근도장 거북이: 솔로·장거리·단골·루틴. */
internal val turtleAxes = linkedMapOf(
    "COMPANION" to AxisLean.A,
    "DISTANCE" to AxisLean.B,
    "COURSE" to AxisLean.A,
    "HABIT" to AxisLean.A
)

/**
 * 16유형 목록: 지금 유형은 출근도장 거북이(확정 이력 있음), 예전에 확정받은 유형은 아침 참새·묵묵한 낙타,
 * 나머지는 한 번도 확정받지 않았다.
 */
internal val typeBoard = RunimalTypeBoard(
    current = "COMMUTER_TURTLE",
    types = RunimalArt.entries.filter { it != RunimalArt.UNKNOWN }.map { art ->
        RunimalTypeBoard.Entry(
            type = art.name,
            displayName = displayNames.getValue(art.name),
            icon = null,
            description = "한 줄 설명",
            axes = if (art == RunimalArt.COMMUTER_TURTLE) turtleAxes else emptyMap(),
            isEverConfirmed =
                art in setOf(RunimalArt.COMMUTER_TURTLE, RunimalArt.MORNING_SPARROW, RunimalArt.STEADY_CAMEL)
        )
    }
)

internal val turtleDetail = RunimalTypeDetail(
    type = "COMMUTER_TURTLE",
    displayName = "출근도장 거북이",
    icon = null,
    description = "같은 길을 같은 시간에, 오래 꾸준히 달려요",
    axes = turtleAxes,
    traits = listOf(
        "혼자 정해 둔 코스를 반복해서 달려요",
        "5km 이상 긴 거리를 편하게 소화해요",
        "비슷한 요일과 시간대에 꾸준히 기록을 남겨요"
    ),
    sharePercent = 33,
    computedAt = Instant.parse("2026-10-29T00:00:00Z")
)

/** RunimalRepository 가짜. 응답은 시험마다 바꾼다. */
internal class FakeRunimal(
    var status: Outcome<RunimalStatus> = Outcome.Failure(AppError.Network),
    var cardResult: Outcome<RunimalCard> = Outcome.Failure(AppError.Network),
    var typesResult: Outcome<RunimalTypeBoard> = Outcome.Success(typeBoard),
    var typeResult: Outcome<RunimalTypeDetail> = Outcome.Success(turtleDetail)
) : RunimalRepository {
    var cardCalls = 0
    val typeCalls = mutableListOf<String>()

    override suspend fun status() = status

    override suspend fun card(): Outcome<RunimalCard> {
        cardCalls++
        return cardResult
    }

    override suspend fun types() = typesResult

    override suspend fun type(type: String): Outcome<RunimalTypeDetail> {
        typeCalls += type
        return typeResult
    }
}
