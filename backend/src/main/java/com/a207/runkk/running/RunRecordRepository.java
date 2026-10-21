package com.a207.runkk.running;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 러닝 기록 저장 포트(설계 6.1.3). 구현은 infra.running.JpaRunRecordRepository이고 running 밖에서 쓰지 않는다(13.1 규칙 4).
 * 아래쪽의 통계·투영 조회는 설계의 "통계 조회(RunQueryService용)"이고 위치·심박 열을 읽지 않는다(6.5).
 */
public interface RunRecordRepository {

    boolean exists(String recordId);

    Optional<RunRecord> findById(String recordId);

    RunRecord save(RunRecord record);

    List<RunRecord> findByMember(long memberId);

    /** 최근 유효 기록 limit개, 최신순. */
    List<RunRecord> recentValid(long memberId, int limit);

    List<RunRecord> findByMemberBetween(long memberId, DateRange range);

    int countValid(long memberId);

    void deleteByMember(long memberId);

    // 통계 조회(RunQueryService용)

    Optional<RunSummary> findSummary(String recordId);

    /** 기간 안 기록 전부(무효 포함), 최신순. */
    List<RunSummary> summariesBetween(long memberId, DateRange range);

    /** 최근 유효 기록 limit개, 최신순. */
    List<RunSummary> recentValidSummaries(long memberId, int limit);

    /** 최근 유효 기록 limit개의 표본, 최신순. */
    List<RunSampleRow> recentValidSamples(long memberId, int limit);

    /** 기간 안 유효 기록의 표본, 최신순. */
    List<RunSampleRow> validSamplesBetween(long memberId, DateRange range);

    /** 날짜별 유효 거리·시간·횟수. 기록이 없는 날은 맵에 없다. */
    Map<LocalDate, DayStats> dailyStats(long memberId, DateRange range);

    /** 여러 회원의 날짜별 유효 거리 합. */
    Map<LocalDate, Distance> dailyDistances(Collection<Long> memberIds, DateRange range);

    TotalStats totals(long memberId);

    /** 회원별 유효 거리 합. 기록이 없는 회원은 맵에 없다. */
    Map<Long, Distance> distancesByMember(Collection<Long> memberIds, DateRange range);

    /** ownerIds 회원의 유효 기록 중 거리 ≥ d이고 경로가 남은 것. */
    List<OwnedRun> validRunsReaching(Distance d, Collection<Long> ownerIds);
}
