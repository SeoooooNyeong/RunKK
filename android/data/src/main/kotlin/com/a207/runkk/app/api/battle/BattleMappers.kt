package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration

// DTO → 도메인 매퍼(13.2 toDomain). 앱이 모르는 대결 종류의 이력 줄은 건너뛴다.

internal fun MatchAcceptanceResponse.toDomain(): MatchAcceptance =
    MatchAcceptance(waiting, distanceMeters?.let(::Distance))

internal fun CandidatesResponse.toDomain(): RecordBattleCandidates = RecordBattleCandidates(
    candidates.map {
        RecordBattleCandidates.Candidate(
            id = it.id,
            displayName = it.displayName,
            distance = Distance(it.distanceMeters),
            finishTime = it.finishSeconds?.let(Duration::ofSeconds),
            recordedOn = it.recordedOn?.let(ServerTime::parseDate),
            sourceLabel = it.sourceLabel,
            estimated = it.estimated
        )
    }
)

/** 상대 표는 실시간 BattleStart.opponentTrack과 같은 TrackTable을 거쳐 OpponentTrack으로 바꾼다(M39). */
internal fun RecordBattleStartResponse.toDomain(): RecordBattleStart = RecordBattleStart(
    battleId = battleId,
    opponentName = opponentName,
    distance = Distance(distanceMeters),
    track = OpponentTrack.from(TrackTable(track.map { TrackPoint(it.elapsedSeconds, it.distanceMeters) })),
    cardKind = enumByName<CardKind>(cardKind) ?: CardKind.SELF_RUN
)

internal fun BattleResultResponse.toDomain(): BattleResult = BattleResult(
    battleId = battleId,
    pending = pending,
    verdict = verdict,
    rows = rows.map { BattleResult.Row(it.item, it.mine, it.theirs) },
    rewardPoints = rewardPoints,
    newRecord = newRecord
)

internal fun BattleHistoryResponse.toDomain(): BattleHistory = BattleHistory(
    entries =
        entries.mapNotNull { entry ->
            enumByName<BattleHistory.Type>(entry.type)?.let { type ->
                BattleHistory.Entry(
                    battleId = entry.battleId,
                    type = type,
                    opponentName = entry.opponentName,
                    distance = Distance(entry.distanceMeters),
                    verdict = entry.verdict,
                    playedAt = ServerTime.parse(entry.playedAt)
                )
            }
        },
    total = total,
    wins = wins,
    losses = losses,
    selfNewRecords = selfNewRecords
)
