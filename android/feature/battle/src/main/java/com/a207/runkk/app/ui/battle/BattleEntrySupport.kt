package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import java.time.Duration

// 랜덤 대결·친구 대결·배치고사 진행 화면이 함께 쓰는 읽기(v0.14 배틀런 화면 D-268·D-271에서 옮김).

/** 처음 고를 거리: 대기 중이면 대기 거리, 아니면 서버 기본 대결 거리(FR-109). 못 읽거나 대결 거리가 아니면 null. */
internal suspend fun BattleRepository.preferredDistance(): Distance? {
    val status = (matchStatus() as? Outcome.Success)?.value ?: return null
    return (if (status.isWaiting) status.distance else status.defaultDistance)?.takeIf { it in MatchUiState.DISTANCES }
}

/** 거리별 내 최고 기록(셀프런 후보, 서버 D-238). 못 읽으면 빈 표다. */
internal suspend fun BattleRepository.bestByDistance(): Map<Distance, Duration?> {
    val candidates = (selfCandidates() as? Outcome.Success)?.value?.candidates ?: return emptyMap()
    return MatchUiState.DISTANCES.associateWith { distance ->
        candidates.firstOrNull { it.distance == distance }?.finishTime
    }
}

/** 앱이 강제 종료돼 남은 친구 초대(최대 5분 동안 랜덤 매칭·배치고사를 막는다)를 취소한다(D-271). */
internal suspend fun BattleRunCoordinator.cancelLeftoverInvite(battles: BattleRepository) {
    val leftover = (battles.myFriendInvite() as? Outcome.Success)?.value ?: return
    cancelInvite(leftover.inviteId).await()
}
