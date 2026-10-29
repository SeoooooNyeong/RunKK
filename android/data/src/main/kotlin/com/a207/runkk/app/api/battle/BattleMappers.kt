package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration

// DTO → 도메인 매퍼(13.2 toDomain). 앱이 모르는 대결 종류의 이력 줄은 건너뛴다.

internal fun MatchAcceptanceResponse.toDomain(): MatchAcceptance =
    MatchAcceptance(waiting, distanceMeters?.let(::Distance), defaultDistanceMeters?.let(::Distance))

internal fun CandidatesResponse.toDomain(): RecordBattleCandidates = RecordBattleCandidates(
    candidates.map {
        RecordBattleCandidates.Candidate(
            id = it.id,
            displayName = it.displayName,
            distance = Distance(it.distanceMeters),
            finishTime = it.finishSeconds?.let(Duration::ofSeconds),
            recordedOn = it.recordedOn?.let(ServerTime::parseDate),
            sourceLabel = it.sourceLabel,
            isEstimated = it.estimated,
            myBest = it.myBestSeconds?.let(Duration::ofSeconds)
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

/** 모르는 대결 종류도 그만두기는 할 수 있게 type만 null로 두고 읽는다. */
internal fun ActiveBattleResponse.toDomain(): ActiveBattle = ActiveBattle(
    battleId = battleId,
    type = enumByName<BattleHistory.Type>(type),
    status = status,
    distance = Distance(distanceMeters),
    opponentName = opponentName?.takeIf { it.isNotBlank() },
    startAt = startAt?.let(ServerTime::parse)
)

internal fun BattleResultResponse.toDomain(): BattleResult = BattleResult(
    battleId = battleId,
    isPending = pending,
    verdict = verdict,
    rows = rows.map { BattleResult.Row(it.item, it.mine, it.theirs) },
    rewardPoints = rewardPoints,
    isNewRecord = newRecord,
    opponentName = opponentName,
    recordId = recordId?.takeIf { it.isNotBlank() },
    myReach = myReachSeconds?.let(Duration::ofSeconds),
    opponentReach = opponentReachSeconds?.let(Duration::ofSeconds),
    type = enumByName<BattleHistory.Type>(type),
    totalRewardPoints = totalRewardPoints,
    evaluation = evaluation?.toDomain()
)

/** tier가 없으면(옛 서버·깨진 값) 평가 없음으로 본다(D-289). */
private fun BattleResultResponse.EvaluationDto.toDomain(): BattleResult.Evaluation? {
    val tier = tier?.takeIf { it.isNotBlank() } ?: return null
    return BattleResult.Evaluation(kind.orEmpty(), tier, validRuns, runimalType)
}

internal fun InviteResponse.toDomain(): FriendInvite = FriendInvite(
    inviteId = inviteId,
    token = token,
    inviteUrl = inviteUrl,
    distance = Distance(distanceMeters),
    expiresAt = ServerTime.parse(expiresAt)
)

internal fun InviteViewResponse.toDomain(): FriendInviteView = FriendInviteView(
    inviteId = inviteId,
    status = status,
    distance = Distance(distanceMeters),
    expiresAt = ServerTime.parse(expiresAt),
    inviter = FriendInviteView.Inviter(inviter.name, inviter.tier, inviter.bestSeconds),
    myTier = me.tier,
    myBestSeconds = me.bestSeconds,
    blockedReason = blockedReason?.takeIf { it.isNotBlank() }
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
                    playedAt = ServerTime.parse(entry.playedAt),
                    gap = entry.gapSeconds?.let(Duration::ofSeconds),
                    sourceLabel = entry.sourceLabel,
                    isNewRecord = entry.newRecord
                )
            }
        },
    total = total,
    wins = wins,
    losses = losses,
    selfNewRecords = selfNewRecords,
    nextCursor = nextCursor
)
