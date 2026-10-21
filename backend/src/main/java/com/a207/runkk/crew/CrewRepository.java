package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Week;
import java.util.List;
import java.util.Optional;

/** 크루 저장소 포트(설계 6.1.7). 구현은 infra.crew.JpaCrewRepository. crew 밖에서 쓰지 않는다(ArchUnit 규칙 4). */
public interface CrewRepository {

    Crew save(Crew crew);

    Optional<Crew> findById(long id);

    /** 크루 행을 잠가(SELECT … FOR UPDATE) 읽는다(5.3 정원). */
    Optional<Crew> findByIdForUpdate(long id);

    Optional<Crew> findByInviteCode(String code);

    boolean existsByName(String name);

    boolean existsByInviteCode(String code);

    /** 회원의 모든 크루 멤버십(퇴장 행 포함). */
    List<CrewMembership> findMembershipHistory(long memberId);

    /** 공개 크루만, 최근 생성순. 이번 주(week) 크루 거리를 붙인다. 크루장 표시명은 비어 있다. */
    List<CrewListRow> search(CrewSearch cond, Week week);

    /** 그 기간에 소속 행이 있던 크루(crew_membership만 읽는다). */
    List<Long> crewIdsActiveBetween(DateRange range);

    /** 소속 중 크루원이 있는 모든 크루의 활동 크루원(랭킹 집계용). */
    List<CrewMembership> findActiveMemberships();

    /** 크루 행과 멤버십을 지운다. */
    void delete(long crewId);
}
