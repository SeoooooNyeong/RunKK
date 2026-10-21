package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.PageCursor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 크루 게시글 저장소 포트(설계 6.1.7). 구현은 infra.crew.JpaCrewPostRepository. */
public interface CrewPostRepository {

    CrewPost save(CrewPost post);

    Optional<CrewPost> findById(long id);

    Optional<CrewRunPost> findCrewRunById(long id);

    /** 모집글 행을 잠가(SELECT … FOR UPDATE) 읽는다. 대기실·러닝 연결·그만두기·시계가 같은 글을 동시에 바꿀 때 줄을 세운다. */
    Optional<CrewRunPost> findCrewRunByIdForUpdate(long id);

    /**
     * 삭제되지 않은 글, 최신순(ID 내림차순). kind가 null이면 두 종류 모두. page.size가 있으면 cursor보다 작은 ID부터 size + 1개까지(다음
     * 페이지가 있는지 보려고 하나 더) 읽는다.
     */
    List<CrewPost> findVisible(long crewId, PostKind kind, PageCursor page);

    /** 시계가 볼 크루런: 삭제 안 됐고 VOTING·LOBBY_OPEN이면서 희망일시가 now + 1시간 안이거나 RUNNING인 것. */
    List<CrewRunPost> findActiveCrewRuns(LocalDateTime now);

    /** 회원이 참여한 크루런(희망일시가 기간 안). Runimal 동행 축·첫 크루런 여부에 쓴다. */
    List<CrewRunPost> findCrewRunsOf(long memberId, DateRange range);

    /** 회원이 쓴 글(탈퇴 익명화). */
    List<CrewPost> findByAuthor(long memberId);

    void deleteByCrew(long crewId);
}
