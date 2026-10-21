package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import java.util.Set;

/**
 * 크루원 한 명의 주간 기여(설계 6.1.7).
 *
 * @param memberId 회원 ID, 탈퇴 뒤 null("탈퇴한 사용자")
 * @param distance 기여 거리 합
 * @param recordIds 더한 기록
 */
public record RaceContribution(Long memberId, Distance distance, Set<String> recordIds) {

    public RaceContribution {
        recordIds = Set.copyOf(recordIds);
    }
}
