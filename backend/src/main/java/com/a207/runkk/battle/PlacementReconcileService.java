package com.a207.runkk.battle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 배치고사 판 셈 누락 복구(05-21 D-490). 판정 사건 수신자가 세 번 다 실패한 판을 최근 7일 안에 끝난 배치고사 대결에서 다시 센다. 셀 조건과 멱등은
 * PlacementRoundListener.count(PlacementRoundCheck, 행 잠금)가 지킨다. batch.battle.PlacementReconcileJob이
 * 매일 0시 20분에 부른다.
 */
@Service
public class PlacementReconcileService {

    private static final Logger log = LoggerFactory.getLogger(PlacementReconcileService.class);
    static final Duration LOOKBACK = Duration.ofDays(7);

    private final BattleRepository battleRepository;
    private final PlacementRoundListener placementRoundListener;

    public PlacementReconcileService(
            BattleRepository battleRepository, PlacementRoundListener placementRoundListener) {
        this.battleRepository = battleRepository;
        this.placementRoundListener = placementRoundListener;
    }

    /** 최근 7일 끝난 배치고사 대결마다 다시 센다. 센(이미 센 것 포함) 판 수를 돌려준다. */
    public int reconcile(LocalDateTime now) {
        List<Long> ids = finishedBetween(now.minus(LOOKBACK), now);
        int counted = 0;
        for (long battleId : ids) {
            if (placementRoundListener.count(battleId)) {
                counted++;
            }
        }
        log.info("placement_reconciled candidates={} counted={}", ids.size(), counted);
        return counted;
    }

    /** 대결 ID만 읽는다(참가자는 count가 새 트랜잭션에서 다시 읽는다). */
    private List<Long> finishedBetween(LocalDateTime from, LocalDateTime to) {
        return battleRepository.findPlacementFinishedBetween(from, to).stream()
                .map(Battle::getId)
                .toList();
    }
}
