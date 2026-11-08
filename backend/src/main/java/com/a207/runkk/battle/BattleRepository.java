package com.a207.runkk.battle;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 대결 저장소 포트(설계 6.1.6). 구현은 infra.battle.JpaBattleRepository. battle 밖에서 쓰지 않는다(ArchUnit 규칙 4). */
public interface BattleRepository {

    Battle save(Battle battle);

    /** 저장하고 바로 저장소에 쓴다. 활성 대결 칸을 비운 뒤 같은 회원의 새 대결을 넣을 때(고유 제약 순서) 쓴다. */
    Battle saveImmediately(Battle battle);

    Optional<Battle> findById(long id);

    /** ID들의 대결(없는 ID는 빠진다). 기록 목록의 대결 결과를 한 번에 읽는다(D-233). */
    List<Battle> findAllById(Collection<Long> ids);

    /** 회원이 참가한 활성 대결(WAITING_START, IN_PROGRESS, COUNTDOWN)이 있는가. */
    boolean existsActive(long memberId);

    /** memberIds 중 활성 대결이 있는 회원(매칭 tick이 대기자 전체를 한 번에 확인, 코드 리뷰 3차 B-L8). */
    Set<Long> activeMemberIds(Collection<Long> memberIds);

    /** 회원이 참가한 활성 대결(많아야 1개, active_member_id 고유 제약). */
    List<Battle> findActive(long memberId);

    /** 출발하지 못한 채 cutoff(now − ABANDON_AFTER) 전에 생성됐거나 출발 시각이 지난 대결(Battle.isAbandoned의 조건). */
    List<Battle> findAbandoned(LocalDateTime cutoff);

    /** startAt ≤ now인 WAITING_START 대결. */
    List<Battle> findStartDue(LocalDateTime now);

    /** 카운트다운이 끝났거나(countdownEndsAt ≤ now) 최대 시간이 지난 진행 중 대결(Battle.isCloseDue). */
    List<Battle> findCountdownDue(LocalDateTime now);

    /** 기록 상대 도착 시각이 지났는데 아직 기록하지 않은 진행 중 대결(Battle.isOpponentArrivalDue). */
    List<Battle> findOpponentArrivalDue(LocalDateTime now);

    /** 보류가 cutoff 이전에 시작된 RESULT_PENDING 대결(cutoff = now − 24시간). */
    List<Battle> findPendingExpired(LocalDateTime cutoff);

    /** 회원이 참가한 FINISHED·RESULT_PENDING 대결, 생성 일시 최신순(FR-101). */
    List<Battle> findHistory(long memberId, DateRange range);

    /**
     * 판정 시각이 [from, to)이고 회원이 이긴 FINISHED 대결, 판정 순(reward 일 단위 보정). 승리 리워드를 주는
     * 종류(BattleType.winRewarded, 친구 대결 제외, D-226)만.
     */
    List<BattleWin> findMemberWinsJudgedBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 판정 시각이 [from, to)인 FINISHED 배치고사 대결(placement_round 있음, 판 셈 누락 복구 D-490, 부분 인덱스
     * ix_battle_placement).
     */
    List<Battle> findPlacementFinishedBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 회원이 이긴 FINISHED 셀럽 대결 중 상대 유명인 기록 ID가 celebrityIds에 있고 판정 시각이 [from, to]인 것의 기록 ID별 첫 판정
     * 시각(event 이벤트 응모, S15P31A207-275).
     */
    Map<Long, LocalDateTime> findFirstCelebrityWins(
            long memberId, Collection<Long> celebrityIds, LocalDateTime from, LocalDateTime to);

    /** 이번 주 전적(FR-093 주간 전적). */
    WeeklyRecord weeklyRecord(long memberId, Week week);
}
