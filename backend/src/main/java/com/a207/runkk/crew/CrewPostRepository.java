package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 크루 게시글 저장소 포트(설계 6.1.7). 구현은 infra.crew.JpaCrewPostRepository. */
public interface CrewPostRepository {

    CrewPost save(CrewPost post);

    Optional<CrewPost> findById(long id);

    Optional<CrewRunPost> findCrewRunById(long id);

    /** 삭제되지 않은 글, 최신순. kind가 null이면 두 종류 모두. */
    List<CrewPost> findVisible(long crewId, PostKind kind);

    /** 시계가 볼 크루런: 삭제 안 됐고 VOTING·LOBBY_OPEN이면서 희망일시가 now + 1시간 안이거나 RUNNING인 것. */
    List<CrewRunPost> findActiveCrewRuns(LocalDateTime now);

    /** 회원이 참여한 크루런(희망일시가 기간 안). Runimal 동행 축·첫 크루런 여부에 쓴다. */
    List<CrewRunPost> findCrewRunsOf(long memberId, DateRange range);

    /** 회원이 쓴 글(탈퇴 익명화). */
    List<CrewPost> findByAuthor(long memberId);

    void deleteByCrew(long crewId);
}
