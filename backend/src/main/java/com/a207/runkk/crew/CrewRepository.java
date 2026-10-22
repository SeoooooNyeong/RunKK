package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.Week;
import java.util.Collection;
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

    /**
     * 공개 크루만, 최근 생성순(ID 내림차순). 키워드·태그 AND와 페이지(cursor보다 ID가 작은 것, size + 1개까지)를 저장소가 거른다(코드 리뷰 2차
     * L13). 더 남았는지 알도록 size보다 하나 더 읽으므로 호출자가 page.slice로 자른다. 이번 주(week) 크루 거리를 붙인다. 크루장 표시명은 비어
     * 있다.
     */
    List<CrewListRow> search(CrewSearch cond, PageCursor page, Week week);

    /** 그 주 크루 거리 합계(주간 거리 행이 없으면 0). */
    Distance weeklyDistance(long crewId, Week week);

    /** 그 ID들의 크루를 한 번에 읽는다(없는 ID는 빠진다, N+1 방지). */
    List<Crew> findAllById(Collection<Long> ids);

    /** 그 기간에 소속 행이 있던 크루(crew_membership만 읽는다). */
    List<Long> crewIdsActiveBetween(DateRange range);

    /** 소속 중 크루원이 있는 모든 크루의 활동 크루원(랭킹 집계용). */
    List<CrewMembership> findActiveMemberships();

    /** 크루 행과 멤버십을 지운다. */
    void delete(long crewId);
}
