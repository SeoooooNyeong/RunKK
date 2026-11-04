package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치고사 판 결과 상태를 battle 안에서 읽는다(05-21 D-513). web.battle의 BattleResultAssembler가 이 값을 옮기기만 해
 * web.battle → member 화살표가 생기지 않는다. 판 인정 규칙은 PlacementRoundCheck 한 곳이다(D-511).
 */
@Component
public class PlacementRoundReader {

    private final BattleRepository battleRepository;
    private final RunQueryService runQueryService;
    private final PlacementService placementService;
    private final Clock clock;

    public PlacementRoundReader(
            BattleRepository battleRepository,
            RunQueryService runQueryService,
            PlacementService placementService,
            Clock clock) {
        this.battleRepository = battleRepository;
        this.runQueryService = runQueryService;
        this.placementService = placementService;
        this.clock = clock;
    }

    /**
     * 참가자의 배치고사 판 상태(D-487). 배치고사 대결이 아니면 비어 있다. 대결이 없거나 취소됐으면 BATTLE_NOT_FOUND, 참가자가 아니면
     * PermissionDeniedException(기존 결과 조회와 같은 오류).
     */
    @Transactional(readOnly = true)
    public Optional<PlacementRoundState> stateOf(long memberId, long battleId) {
        Battle battle =
                battleRepository
                        .findById(battleId)
                        .filter(b -> !b.isCancelled())
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_NOT_FOUND));
        if (!battle.isParticipant(memberId)) {
            throw new PermissionDeniedException("대결 참가자가 아니다");
        }
        if (battle.placementRound().isEmpty()) {
            return Optional.empty();
        }
        Boolean valid =
                battle.participant(memberId)
                        .flatMap(BattleParticipant::getRecordId)
                        .flatMap(runQueryService::findSummary)
                        .map(RunSummary::valid)
                        .orElse(null);
        PlacementRoundCheck check =
                PlacementRoundCheck.of(battle, memberId, valid, LocalDateTime.now(clock));
        return Optional.of(
                PlacementRoundState.of(
                        check,
                        placementService.isCounted(memberId, battleId),
                        placementService.isCompleted(memberId)));
    }
}
