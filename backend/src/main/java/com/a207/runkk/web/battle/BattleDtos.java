package com.a207.runkk.web.battle;

import com.a207.runkk.battle.BattleHistoryView;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.ComparisonTable;
import com.a207.runkk.battle.MatchAcceptance;
import com.a207.runkk.battle.RecordBattleCandidates;
import com.a207.runkk.battle.RecordBattleStart;
import com.a207.runkk.battle.Source;
import com.a207.runkk.battle.TrackSample;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.battle.WeeklyRecord;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대결 REST 전송 형식(설계 5.7.1, 10/21 API 계약). 거리는 미터, 시간은 초, 시각은 서버 시간대 ISO-8601, 날짜는 yyyy-MM-dd. 앱
 * api.battle DTO와 같은 항목 이름을 쓴다.
 */
final class BattleDtos {

    private static final String SELF_CARD = "SELF_RUN";
    private static final String CELEBRITY_CARD = "CELEB_RUN";

    private BattleDtos() {}

    /**
     * 랜덤 대결·나와의 싸움 거리.
     *
     * @param distanceMeters 대결 거리(미터)
     */
    record DistanceRequest(@Positive long distanceMeters) {}

    /**
     * 유명인 대결.
     *
     * @param celebrityId 유명인 기록 ID
     */
    record CelebrityBattleRequest(@Positive long celebrityId) {}

    /**
     * 매칭 접수 결과.
     *
     * @param waiting 대기 중인가
     * @param distanceMeters 대기 거리
     */
    record MatchAcceptanceResponse(boolean waiting, Long distanceMeters) {
        static MatchAcceptanceResponse of(MatchAcceptance a) {
            return new MatchAcceptanceResponse(
                    a.waiting(), a.distance() == null ? null : a.distance().distance().meters());
        }
    }

    /**
     * 기록 대결 후보.
     *
     * @param candidates 후보
     */
    record CandidatesResponse(List<Candidate> candidates) {
        static CandidatesResponse of(RecordBattleCandidates c) {
            return new CandidatesResponse(c.candidates().stream().map(Candidate::of).toList());
        }
    }

    /**
     * 후보 하나.
     *
     * @param id 유명인 기록 ID(나와의 싸움은 null)
     * @param displayName 표시명
     * @param distanceMeters 거리
     * @param finishSeconds 완주 시간(초), 기록이 없으면 null
     * @param recordedOn 기록일
     * @param sourceLabel 출처 표기("공개 기록 기준 · …")
     * @param estimated 평균 페이스로 추정
     */
    record Candidate(
            Long id,
            String displayName,
            long distanceMeters,
            Long finishSeconds,
            LocalDate recordedOn,
            String sourceLabel,
            boolean estimated) {
        static Candidate of(RecordBattleCandidates.Candidate c) {
            return new Candidate(
                    c.celebrityId(),
                    c.displayName(),
                    c.distance().meters(),
                    seconds(c.finishTime()),
                    c.recordedOn(),
                    Optional.ofNullable(c.source()).map(Source::label).orElse(null),
                    c.estimated());
        }
    }

    /**
     * 기록 대결 시작 정보(design-review.md M34). track은 실시간 BattleStart.opponentTrack과 같은 형식이다.
     *
     * @param battleId 대결 ID
     * @param opponentName 상대 표시명
     * @param distanceMeters 대결 거리
     * @param track 상대 경과 시간·누적 거리 표
     * @param cardKind 러닝 카드 종류(SELF_RUN, CELEB_RUN)
     * @param sourceLabel 출처 표기(유명인)
     * @param estimated 평균 페이스로 추정
     */
    record RecordBattleStartResponse(
            long battleId,
            String opponentName,
            long distanceMeters,
            List<TrackPointResponse> track,
            String cardKind,
            String sourceLabel,
            boolean estimated) {
        static RecordBattleStartResponse of(RecordBattleStart s) {
            return new RecordBattleStartResponse(
                    s.battleId(),
                    s.opponentName().value(),
                    s.distance().meters(),
                    s.track().wholeSeconds().stream().map(TrackPointResponse::of).toList(),
                    s.type() == BattleType.SELF ? SELF_CARD : CELEBRITY_CARD,
                    Optional.ofNullable(s.source()).map(Source::label).orElse(null),
                    s.estimated());
        }
    }

    /**
     * 상대 표 한 점.
     *
     * @param elapsedSeconds 경과 초
     * @param distanceMeters 누적 미터
     */
    record TrackPointResponse(long elapsedSeconds, long distanceMeters) {
        static TrackPointResponse of(TrackSample s) {
            return new TrackPointResponse(s.elapsed().toSeconds(), s.distance().meters());
        }
    }

    /**
     * 대결 결과(FR-093, FR-108). rewardPoints는 BattleResultAssembler가 붙인다(D-33).
     *
     * @param battleId 대결 ID
     * @param type 대결 종류(이력 분류, 고스트는 RANDOM)
     * @param pending 결과 보류
     * @param retryUpload 기록 업로드 재시도 안내
     * @param verdict 판정
     * @param corrected 정정 여부
     * @param myReachSeconds 본인 도달 시간(초)
     * @param opponentReachSeconds 상대 도달 시간(초)
     * @param rows 비교표
     * @param weekly 이번 주 전적
     * @param rewardPoints 적립 리워드, 아직 없으면 null
     * @param newRecord 신기록
     */
    record BattleResultResponse(
            long battleId,
            String type,
            boolean pending,
            boolean retryUpload,
            Verdict verdict,
            boolean corrected,
            Long myReachSeconds,
            Long opponentReachSeconds,
            List<Row> rows,
            WeeklyRecord weekly,
            Integer rewardPoints,
            boolean newRecord) {}

    /**
     * 비교표 한 줄.
     *
     * @param item 항목 이름
     * @param mine 본인 값
     * @param theirs 상대 값
     */
    record Row(String item, String mine, String theirs) {
        static List<Row> of(ComparisonTable table) {
            return table.rows().stream()
                    .map(r -> new Row(r.item().label(), r.mine(), r.theirs()))
                    .toList();
        }
    }

    /**
     * 대결 이력(FR-101).
     *
     * @param entries 최신순
     * @param total 대결 수
     * @param wins 승
     * @param losses 패
     * @param selfNewRecords 나와의 싸움 신기록 수
     */
    record BattleHistoryResponse(
            List<Entry> entries, int total, int wins, int losses, int selfNewRecords) {
        static BattleHistoryResponse of(BattleHistoryView v) {
            return new BattleHistoryResponse(
                    v.entries().stream().map(Entry::of).toList(),
                    v.total(),
                    v.wins(),
                    v.losses(),
                    v.selfNewRecords());
        }
    }

    /**
     * 이력 한 줄.
     *
     * @param battleId 대결 ID
     * @param type 이력 분류
     * @param opponentName 상대 표시명
     * @param distanceMeters 대결 거리
     * @param verdict 판정
     * @param playedAt 대결 일시
     * @param newRecord 신기록
     */
    record Entry(
            long battleId,
            String type,
            String opponentName,
            long distanceMeters,
            Verdict verdict,
            LocalDateTime playedAt,
            boolean newRecord) {
        static Entry of(BattleHistoryView.Entry e) {
            return new Entry(
                    e.battleId(),
                    e.type().name(),
                    e.opponentName().value(),
                    e.distance().meters(),
                    e.verdict(),
                    e.playedAt(),
                    e.newRecord());
        }
    }

    static Long seconds(Duration d) {
        return d == null ? null : d.toSeconds();
    }
}
