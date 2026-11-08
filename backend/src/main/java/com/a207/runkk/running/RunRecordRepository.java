package com.a207.runkk.running;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** 회원의 기록과 기록 삭제 묘비를 모두 지운다(탈퇴, FR-021). */
    void deleteByMember(long memberId);

    // v0.14 기록 삭제(FR-225, D-231·D-232)

    /** 기록 행 하나를 지운다. 위치 경로·심박·구간·날씨는 같은 행의 열이라 함께 지워진다. */
    void delete(RunRecord r);

    /** 기록 삭제 묘비(run_record_tombstone)를 남긴다. 같은 기록 ID의 재업로드를 막는다. */
    void bury(String recordId, long memberId, LocalDateTime at);

    /** 묘비가 있는 기록 ID인가. */
    boolean isBuried(String recordId);

    // v0.14 목록·썸네일(D-233·D-234·D-292)

    /** 기간 안 기록 목록 줄(무효 포함), 최신순. 위치·심박 열은 읽지 않는다. */
    List<RunListRow> listRows(long memberId, DateRange r);

    /** 시작 시각이 가장 늦은 기록 1건의 목록 줄(무효 포함). 없으면 비어 있다(홈 최근 러닝, 코드 리뷰 PL1-B5). */
    Optional<RunListRow> latestListRow(long memberId);

    /**
     * 경로 모양이 없고 경로가 남은 기록 중 기록 ID가 afterRecordId보다 큰 것을 ID 순으로 limit개(되채우기). 채우지 못한 기록이 앞에 쌓여도
     * 마지막으로 본 ID부터 이어 가므로 뒤 기록이 막히지 않는다.
     */
    List<RunRecord> findMissingShape(String afterRecordId, int limit);

    // v0.14 평가 표시(D-291·D-294)

    /** 그 기록이 일으킨 평가(평가 종류가 없으면 비어 있다). */
    Optional<EvaluationView> evaluationOf(String recordId);

    /** 그 기록에 Runimal 확정 유형 이름을 쓴다(한 열 갱신, 순서 무관). 기록이 없으면 아무것도 하지 않는다. */
    void noteRunimalType(String recordId, String runimalType);

    // 통계 조회(RunQueryService용)

    Optional<RunSummary> findSummary(String recordId);

    /** 기간 안 기록 전부(무효 포함), 최신순. */
    List<RunSummary> summariesBetween(long memberId, DateRange range);

    /**
     * 모든 회원의 유효 기록 가운데 거리 minMeters 이상이고 시작 시각이 [from, to]인 것, 시작 시각 오래된 순(event 이벤트 응모자 명단,
     * S15P31A207-275).
     */
    List<RunSummary> validSummariesAtLeast(long minMeters, LocalDateTime from, LocalDateTime to);

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

    /** 저장 일시가 [from, to)인 기록 전부(무효 포함), 저장 순. 커밋 뒤 수신자 보정 배치용. */
    List<SavedRun> savedBetween(LocalDateTime from, LocalDateTime to);

    /** 그 회원의 유효 기록 중 저장 일시 &lt; cutoff인 것의 거리 합(없으면 0). */
    Distance validDistanceSavedBefore(long memberId, LocalDateTime cutoff);

    /**
     * 그 회원의 유효 기록 중 유효 러닝 순번 &gt; ordinal인 것, 저장 일시·기록 ID 순(D-108). 순번은 같은 순서로 센 유효 기록의 차례(1부터)다.
     * 업로드가 회원 단위로 직렬화되어(5.3) 저장 때 매긴 순번(유효 기록 수 + 1)과 같다.
     */
    List<ValidRunScore> validRunsAfter(long memberId, int ordinal);

    /**
     * 그 회원의 유효 기록 중 유효 러닝 순번이 fromOrdinal 이상 toOrdinal 이하인 것, 저장 일시·기록 ID 순(validRunsAfter와 같은 순번).
     * 위치·심박 원자료 열은 읽지 않는다(런바디 요약 G-14).
     */
    List<ValidRunStat> validRunStats(long memberId, int fromOrdinal, int toOrdinal);

    /**
     * 저장 일시가 [from, to)이고 티어에 아직 반영하지 않은(tier_applied = false) 유효 기록, 저장 일시·기록 ID 순(총괄 결정 7-3). 색인
     * idx_run_record_tier_pending.
     */
    List<SavedRun> tierPendingBetween(LocalDateTime from, LocalDateTime to);

    /** 유효 기록 하나의 점수·거리와 유효 러닝 순번(그 회원의 유효 기록을 저장 일시·기록 ID 순으로 센 차례). 무효거나 없으면 비어 있음. */
    Optional<ValidRunScore> validRunScoreOf(String recordId);

    /**
     * 티어 반영 표시와 평가 표시(있으면 종류·티어·순번, D-291)를 함께 세운다(조건부 갱신). 이번에 세웠으면 true, 이미 서 있거나 기록이 없으면 false.
     */
    boolean markTierApplied(String recordId, Optional<EvaluationView> evaluation);

    // v0.10 일회성 재판정(RunRejudgeService, V28)

    /** 재판정 표시가 state인 기록 ID, 저장 일시·기록 ID 순으로 limit개. 색인 idx_run_record_rejudge. */
    List<String> rejudgeCandidates(RejudgeState state, int limit);

    /** 행 잠금을 걸고 읽는다. 재판정이 여러 인스턴스에서 겹쳐도 한쪽만 표시를 바꾼다. */
    Optional<RunRecord> findForUpdate(String recordId);

    /**
     * 재판정 결과(거리·시간·평균 페이스·유효·점수·제외 거리·제외 시간)를 쓰고 표시를 from에서 to로 바꾼다(조건부 갱신). 거리·시간 열은 엔티티 저장으로 바꾸지
     * 않으므로(updatable = false) 이 연산만 쓴다. 표시가 from이 아니면 아무것도 하지 않고 false.
     */
    boolean storeRejudgement(RunRecord record, RejudgeState from, RejudgeState to);

    /** 재판정 표시만 from에서 to로 바꾼다(조건부 갱신). 표시가 from이 아니면 false. */
    boolean moveRejudgeState(String recordId, RejudgeState from, RejudgeState to);
}
