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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치고사 판 결과 상태를 battle 안에서 읽는다(05-21 D-513). web.battle의 BattleResultAssembler가 이 값을 옮기기만 해
 * web.battle → member 화살표가 생기지 않는다. 판 인정 규칙은 PlacementRoundCheck 한 곳이다(D-511).
 */
@Component
public class PlacementRoundReader {

    private static final Logger log = LoggerFactory.getLogger(PlacementRoundReader.class);

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
        LocalDateTime now = LocalDateTime.now(clock);
        PlacementRoundCheck check = PlacementRoundCheck.of(battle, memberId, valid, now);
        PlacementRoundState state =
                PlacementRoundState.of(
                        check,
                        placementService.isCounted(memberId, battleId),
                        placementService.isCompleted(memberId));
        if (state == PlacementRoundState.PENDING
                && check.countable()
                && recoveryClosed(battle, now)) {
            // 셀 수 있는데 세 계기가 모두 실패한 채 복구 창이 지났다. 더 셀 계기가 없어 닫는다(코드 리뷰 PL1-B8)
            log.warn("placement_round_unrecovered battleId={} memberId={}", battleId, memberId);
            return Optional.of(PlacementRoundState.NOT_COUNTED);
        }
        return Optional.of(state);
    }

    /** 판정 뒤 복구 작업이 다시 보는 기간(PlacementReconcileService.LOOKBACK, 7일)이 지났는가. */
    private static boolean recoveryClosed(Battle battle, LocalDateTime now) {
        return battle.getJudgement()
                .map(j -> !now.isBefore(j.at().plus(PlacementReconcileService.LOOKBACK)))
                .orElse(false);
    }
}
