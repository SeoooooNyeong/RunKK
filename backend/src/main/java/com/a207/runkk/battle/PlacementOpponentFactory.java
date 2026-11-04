package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.PlacementBasis;
import com.a207.runkk.policy.TierBoundary;
import com.a207.runkk.policy.VirtualRecordRule;
import com.a207.runkk.running.RunningScore;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 배치고사 상대 기록을 만든다(05-21 2.2, D-480, A-38). 상대 점수: 1판 = 가상 기록 규칙의 하한 점수(기본 27, 브론즈 아래 열린 구간을 닫는 점수),
 * 2·3판 = 임시 티어 구간의 가운데 점수. 그 점수의 페이스로 고른 거리를 일정하게 달린다(오프셋 0). 경계값·규칙은 대결을 만드는 순간 넘겨받은 값이고 대결이 표를 남겨
 * 뒤에 바뀌어도 그 판은 그대로다(J24-13).
 */
@Component
public class PlacementOpponentFactory {

    private final VirtualRecordRepository virtualRecordRepository;
    private final RandomGenerator random;

    public PlacementOpponentFactory(
            VirtualRecordRepository virtualRecordRepository, RandomGenerator battleRandom) {
        this.virtualRecordRepository = virtualRecordRepository;
        this.random = battleRandom;
    }

    /** 상대를 만든다. 닉네임을 빌릴 같은 거리 가상 기록이 없으면 BusinessRuleException(MATCH_FAILED). */
    public PlacementOpponent create(
            PlacementBasis basis,
            StandardDistance distance,
            TierBoundary boundary,
            VirtualRecordRule rule) {
        double score = opponentScore(basis, boundary, rule);
        Duration finish =
                VirtualRecordFactory.finishTime(
                        new RunningScore(score).paceFor(distance.distance()),
                        0,
                        distance.distance());
        return new PlacementOpponent(
                basis.nextRound(), borrowedName(basis, distance), distance, finish);
    }

    /** A-38. 1판이면 rule.openLowScore, 아니면 임시 티어 가운데 점수(임시 티어가 없으면 IllegalStateException). */
    public static double opponentScore(
            PlacementBasis basis, TierBoundary boundary, VirtualRecordRule rule) {
        if (basis.nextRound() == 1) {
            return rule.openLowScore();
        }
        if (basis.provisionalTier() == null) {
            throw new IllegalStateException("2·3판인데 임시 티어가 없다: " + basis);
        }
        return boundary.midScoreOf(basis.provisionalTier(), rule);
    }

    /** 같은 거리 가상 기록(임시 티어, 없으면 브론즈) 가운데 하나의 닉네임(FR-104 이름이라 회원 닉네임과 겹치지 않는다). */
    private DisplayName borrowedName(PlacementBasis basis, StandardDistance distance) {
        Tier tier = basis.provisionalTier() == null ? Tier.BRONZE : basis.provisionalTier();
        List<VirtualRecord> records = virtualRecordRepository.findByTierAndDistance(tier, distance);
        if (records.isEmpty()) {
            throw new BusinessRuleException(ReasonCode.MATCH_FAILED);
        }
        return records.get(random.nextInt(records.size())).displayName();
    }
}
