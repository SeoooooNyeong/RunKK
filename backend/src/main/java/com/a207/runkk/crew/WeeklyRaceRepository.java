package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import java.util.Optional;

/**
 * 크루 주간 거리 저장소 포트(설계 6.1.7, 6.2 A-12). 구현은 infra.crew.JpaWeeklyRaceRepository. 합산은 메모리에서 읽고-더하고-쓰지
 * 않고 addAtomically 한 번으로 한다(5.3).
 */
public interface WeeklyRaceRepository {

    /** 기여 행 삽입이 성공했을 때만 합계를 원자적으로 늘리고 true. 같은 (크루, 주, 기록)이 이미 있으면 false. */
    boolean addAtomically(RaceContributionKey key, Distance d);

    Optional<WeeklyRace> find(long crewId, Week week);

    /** 기록 하나의 반영 거리(어느 크루·주든), 없으면 0. */
    Distance contributionOf(String recordId);

    void deleteByCrew(long crewId);

    /** 탈퇴 회원의 기여를 익명화한다(합계는 그대로, FR-021). */
    void anonymizeMember(long memberId);
}
