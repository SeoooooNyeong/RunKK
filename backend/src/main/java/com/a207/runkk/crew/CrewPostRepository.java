package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.PageCursor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 크루 게시글 저장소 포트(설계 6.1.7, D-99, D-100). 구현은 infra.crew.JpaCrewPostRepository. */
public interface CrewPostRepository {

    /** 새 글이면 바로 INSERT까지 한다(사진 메타 ID가 PostPhotoStore.save 전에 필요하다). */
    CrewPost save(CrewPost post);

    Optional<CrewPost> findById(long id);

    /**
     * 글 행을 잠가(SELECT … FOR UPDATE) 최신 상태로 읽는다. 일정 참석 응답(정원·응답 경합)·공지 고정·투표 응답·삭제·숨김이 같은 글을 동시에 바꿀 때
     * 줄을 세운다(5.3).
     */
    Optional<CrewPost> findByIdForUpdate(long id);

    Optional<CrewRunPost> findCrewRunById(long id);

    /** 모집글 행을 잠가(SELECT … FOR UPDATE) 읽는다. 대기실·러닝 연결·그만두기·시계가 같은 글을 동시에 바꿀 때 줄을 세운다. */
    Optional<CrewRunPost> findCrewRunByIdForUpdate(long id);

    /**
     * 삭제·숨김을 뺀 글, 최신순(ID 내림차순). 종류가 비면 전부, pinnedOnly면 고정 공지만. page.size가 있으면 cursor보다 작은 ID부터 size
     * + 1개까지 읽는다(FR-212).
     */
    List<CrewPost> findVisible(long crewId, PostQuery query);

    /** 보이는(삭제·숨김 아닌) 고정 공지 수(D-154). */
    int countPinnedNotices(long crewId);

    /** 마감이 [from, to]이고 아직 알리지 않은 보이는 투표(D-106, D-125, 부분 색인 idx_crew_post_poll_closing). */
    List<PollPost> findClosingPolls(LocalDateTime from, LocalDateTime to);

    /** 시계가 볼 크루런: 삭제 안 됐고 VOTING·LOBBY_OPEN이면서 희망일시가 now + 1일 안이거나 RUNNING인 것. */
    List<CrewRunPost> findActiveCrewRuns(LocalDateTime now);

    /** 크루 삭제 때 끝낼 크루런: 그 크루의 삭제 안 된 LOBBY_OPEN·RUNNING 글(D-66). */
    List<CrewRunPost> findActiveCrewRunsOf(long crewId);

    /** 회원이 참여한 크루런(희망일시가 기간 안). Runimal 동행 축·첫 크루런 여부에 쓴다. */
    List<CrewRunPost> findCrewRunsOf(long memberId, DateRange range);

    /**
     * 지난 크루런(W6): 그 크루의 보이는(삭제·숨김 아닌) 크루런 중 FINISHED·CANCELLED이고 일시가 [from, to)인 글, 일시 최신순(같으면 ID
     * 내림차순). 부분 색인 idx_crew_post_crew_run_past.
     */
    List<CrewRunPost> findPastCrewRuns(long crewId, LocalDateTime from, LocalDateTime to);

    /** 그 크루의 가장 이른 지난 크루런 일시(W6, 기간 휠 시작). 없으면 비어 있다. */
    Optional<LocalDateTime> findEarliestPastCrewRun(long crewId);

    /** 회원이 쓴 글(탈퇴 익명화). */
    List<CrewPost> findByAuthor(long memberId);

    Optional<PostPhoto> findPhoto(long photoId);

    /** 보이는 글의 사진, photoId 내림차순. page.size가 있으면 size + 1개까지. */
    List<PostPhoto> findAlbum(long crewId, PageCursor page);

    /**
     * 크루 탈퇴·강제 퇴장의 응답 정리(D-146): 그 크루의 삭제되지 않은 일정 중 scheduled_at &gt; now인 글의 그 회원
     * crew_run_participation·crew_meetup_attendance 행과 closes_at &gt; now인 투표의 그 회원
     * crew_poll_response 행을 지우고 지운 수를 돌려준다.
     */
    int deleteOpenResponses(long crewId, long memberId, LocalDateTime now);

    /** 회원 탈퇴(FR-021, D-117): 그 회원의 투표 응답과 대면 모임 참석 행을 모두 지운다. */
    int deleteResponsesByMember(long memberId);

    /** 크루의 글과 딸린 행(참여·참석·선택지·응답·사진)과 크루런 반복(crew_run_series, W6)을 모두 지운다. */
    void deleteByCrew(long crewId);
}
