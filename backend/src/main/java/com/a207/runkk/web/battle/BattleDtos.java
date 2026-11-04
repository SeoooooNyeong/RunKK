package com.a207.runkk.web.battle;

import com.a207.runkk.battle.ActiveBattleView;
import com.a207.runkk.battle.BattleHistoryView;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.ComparisonTable;
import com.a207.runkk.battle.MatchAcceptance;
import com.a207.runkk.battle.PlacementRoundState;
import com.a207.runkk.battle.RecordBattleCandidates;
import com.a207.runkk.battle.RecordBattleStart;
import com.a207.runkk.battle.RecordTrack;
import com.a207.runkk.battle.Source;
import com.a207.runkk.battle.TrackSample;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.battle.WeeklyRecord;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.running.EvaluationView;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;

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
     * 대결 이력 조회 쿼리(GET /battles/history, 코드 리뷰 2차 P1). 쿼리 이름은 항목 이름과 같다.
     *
     * @param type RANDOM(고스트 포함)·SELF·CELEBRITY, 없으면 전부
     * @param from 시작일(ISO 날짜), 없으면 12개월 전
     * @param to 끝일(ISO 날짜), 없으면 오늘
     * @param cursor 다음 페이지 커서(직전 응답 nextCursor)
     * @param size 페이지 크기(없으면 전부)
     */
    record HistoryQuery(
            BattleType type,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Long cursor,
            Integer size) {

        PageCursor page() {
            return new PageCursor(cursor, size);
        }
    }

    /**
     * 매칭 접수 결과.
     *
     * @param waiting 대기 중인가
     * @param distanceMeters 대기 거리
     * @param defaultDistanceMeters 기본 대결 거리(마지막으로 요청한 거리)
     * @param placementRound 배치고사로 만든 대결의 판 번호(1~3), 그 밖은 null(05-03 5.7.3.3 P2)
     * @param battleId 배치고사로 만든 대결 ID, 그 밖은 null
     */
    record MatchAcceptanceResponse(
            boolean waiting,
            Long distanceMeters,
            Long defaultDistanceMeters,
            Integer placementRound,
            Long battleId) {
        static MatchAcceptanceResponse of(MatchAcceptance acceptance) {
            return new MatchAcceptanceResponse(
                    acceptance.waiting(),
                    metersOf(acceptance.distance()),
                    metersOf(acceptance.defaultDistance()),
                    null,
                    null);
        }

        private static Long metersOf(StandardDistance d) {
            return d == null ? null : d.distance().meters();
        }
    }

    /**
     * 진행 중인 대결(GET /battles/active, 배틀런 QA 7번). 시각은 다른 대결 응답과 같이 오프셋 없는 서버 시간대 ISO-8601이다.
     *
     * @param battleId 대결 ID
     * @param type 대결 종류(RANDOM, GHOST, SELF, CELEBRITY)
     * @param status 대결 상태(WAITING_START, IN_PROGRESS, COUNTDOWN)
     * @param distanceMeters 대결 거리
     * @param opponentName 상대 표시명, 없으면 null
     * @param startAt 출발 시각, 없으면 null
     * @param opponentGender 상대 캐릭터 성별(MALE, FEMALE), 셀럽·나와의 싸움이면 null(2026-10-31 결정)
     * @param celebrityFigure 유명인 캐릭터 그림 키, 유명인 대결이 아니거나 키가 없으면 null(앱 공통 셀럽 그림, 2026-10-31 결정)
     * @param placementRound 배치고사 대결이면 판 번호(1~3), 아니면 null(05-03 5.7.3.3 P5)
     */
    record ActiveBattleResponse(
            long battleId,
            String type,
            String status,
            long distanceMeters,
            String opponentName,
            LocalDateTime startAt,
            String opponentGender,
            String celebrityFigure,
            Integer placementRound) {
        static ActiveBattleResponse of(ActiveBattleView v) {
            return new ActiveBattleResponse(
                    v.battleId(),
                    v.type().name(),
                    v.status().name(),
                    v.distance().meters(),
                    Optional.ofNullable(v.opponentName()).map(DisplayName::value).orElse(null),
                    v.startAt(),
                    v.opponentGender(),
                    v.celebrityFigure(),
                    null);
        }
    }

    /**
     * 기록 대결 후보.
     *
     * @param candidates 후보
     */
    record CandidatesResponse(List<Candidate> candidates) {
        static CandidatesResponse of(RecordBattleCandidates candidates) {
            return new CandidatesResponse(
                    candidates.candidates().stream().map(Candidate::of).toList());
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
     * @param sourceLabel 출처 표기("출처: …", v0.14 D-239)
     * @param estimated 평균 페이스로 추정
     * @param myBestSeconds 그 거리 내 최고 기록(초), 없으면 null(셀럽 카운트다운의 "첫 도전", v0.14 D-238)
     * @param celebrityFigure 유명인 캐릭터 그림 키, 없거나 나와의 싸움 후보면 null(2026-10-31 결정)
     */
    record Candidate(
            Long id,
            String displayName,
            long distanceMeters,
            Long finishSeconds,
            LocalDate recordedOn,
            String sourceLabel,
            boolean estimated,
            Long myBestSeconds,
            String celebrityFigure) {
        static Candidate of(RecordBattleCandidates.Candidate c) {
            return new Candidate(
                    c.celebrityId(),
                    c.displayName(),
                    c.distance().meters(),
                    seconds(c.finishTime()),
                    c.recordedOn(),
                    Optional.ofNullable(c.source()).map(Source::label).orElse(null),
                    c.estimated(),
                    seconds(c.myBest()),
                    c.figureKey());
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
     * @param celebrityFigure 유명인 캐릭터 그림 키, 없거나 나와의 싸움이면 null(2026-10-31 결정)
     */
    record RecordBattleStartResponse(
            long battleId,
            String opponentName,
            long distanceMeters,
            List<TrackPointResponse> track,
            String cardKind,
            String sourceLabel,
            boolean estimated,
            String celebrityFigure) {
        static RecordBattleStartResponse of(RecordBattleStart s) {
            return new RecordBattleStartResponse(
                    s.battleId(),
                    s.opponentName().value(),
                    s.distance().meters(),
                    s.track().wholeSeconds().stream().map(TrackPointResponse::of).toList(),
                    s.type() == BattleType.SELF ? SELF_CARD : CELEBRITY_CARD,
                    Optional.ofNullable(s.source()).map(Source::label).orElse(null),
                    s.estimated(),
                    s.figureKey());
        }
    }

    /**
     * 워치 상대 기록 미리 받기(GET /battles/record-tracks, v0.21 D-377). 거리 → 완주 시간 오름차순.
     *
     * @param tracks 상대 기록
     */
    record RecordTracksResponse(List<RecordTrackResponse> tracks) {
        static RecordTracksResponse of(List<RecordTrack> tracks) {
            return new RecordTracksResponse(tracks.stream().map(RecordTrackResponse::of).toList());
        }
    }

    /**
     * 워치 상대 기록 하나. track은 기록 대결 시작 정보(RecordBattleStartResponse.track)와 같은 형식이다.
     *
     * @param celebrityId 유명인 기록 ID, 나와의 싸움이면 null
     * @param opponentName 상대 표시명(RecordBattleStartResponse.opponentName과 같은 규칙)
     * @param distanceMeters 대결 거리
     * @param finishSeconds 상대 기록 완주 시간(초)
     * @param track 상대 경과 시간·누적 거리 표. 셀럽 목록(kind=CELEBRITY)에서는 빈 목록이고 단건 조회에서 채운다(W31)
     * @param sourceLabel 출처 표기(유명인), 나와의 싸움이면 null
     * @param estimated 구간 기록이 없어 균등 페이스로 만든 표(A-006)
     * @param celebrityFigure 유명인 캐릭터 그림 키, 없거나 나와의 싸움이면 null
     */
    record RecordTrackResponse(
            Long celebrityId,
            String opponentName,
            long distanceMeters,
            long finishSeconds,
            List<TrackPointResponse> track,
            String sourceLabel,
            boolean estimated,
            String celebrityFigure) {
        static RecordTrackResponse of(RecordTrack t) {
            return new RecordTrackResponse(
                    t.celebrityId(),
                    t.opponentName().value(),
                    t.distance().meters(),
                    t.finishTime().toSeconds(),
                    t.track().isEmpty()
                            ? List.of()
                            : t.track().wholeSeconds().stream()
                                    .map(TrackPointResponse::of)
                                    .toList(),
                    Optional.ofNullable(t.source()).map(Source::label).orElse(null),
                    t.estimated(),
                    t.figureKey());
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
     * 대결 결과(FR-093, FR-108). rewardPoints·totalRewardPoints·evaluation은 BattleResultAssembler가
     * 붙인다(D-33, v0.14 D-229·D-291, 12.2 W5). rows는 SELF만 두 줄이고 나머지는 빈 목록이다. weekly는 옛 앱용으로 남긴다(폐기
     * 예정). 필드는 더하기만 한다.
     *
     * @param battleId 대결 ID
     * @param type 대결 종류(이력 분류, 고스트는 RANDOM, 친구 대결은 FRIEND)
     * @param pending 결과 보류
     * @param retryUpload 기록 업로드 재시도 안내
     * @param verdict 판정
     * @param corrected 정정 여부
     * @param myReachSeconds 본인 도달 시간(초)
     * @param opponentReachSeconds 상대 도달 시간(초)
     * @param rows 비교표
     * @param weekly 이번 주 전적
     * @param rewardPoints 이 대결로 적립한 리워드(BATTLE_WIN), 없으면 0
     * @param newRecord 신기록
     * @param opponentName 상대 표시명
     * @param recordId 본인 러닝 기록 ID(기록 카드), 아직 없거나 기록을 지웠으면 null
     * @param totalRewardPoints 이 대결로 적립한 합계 = BATTLE_WIN(대결) + VALID_RUN(본인 기록), v0.14 D-229. 친구
     *     대결은 거리 적립만
     * @param evaluation 본인 기록이 일으킨 티어 평가(없으면 null, v0.14 D-291)
     * @param opponentGender 상대 캐릭터 성별(MALE, FEMALE), 셀럽·나와의 싸움이면 null(2026-10-31 결정)
     * @param celebrityFigure 유명인 캐릭터 그림 키, 유명인 대결이 아니거나 키가 없으면 null(2026-10-31 결정)
     * @param placementRound 배치고사 대결이면 만든 때의 판 번호(1~3), 아니면 null(05-03 5.7.3.3 P4)
     * @param placementState 배치고사 판 셈 상태(COUNTED·NOT_COUNTED·PENDING), placementRound가 null이면 null
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
            boolean newRecord,
            String opponentName,
            String recordId,
            int totalRewardPoints,
            EvaluationDto evaluation,
            String opponentGender,
            String celebrityFigure,
            Integer placementRound,
            PlacementRoundState placementState) {}

    /**
     * 이 러닝이 일으킨 티어 평가(v0.14 D-291·D-294).
     *
     * @param kind FIRST_PLACEMENT 또는 REEVALUATION
     * @param tier 평가 뒤 티어
     * @param validRuns 평가를 일으킨 유효 러닝 순번(3, 10의 배수)
     * @param runimalType 그 판정에서 확정된 Runimal 유형(첫 배치·미확정이면 null)
     */
    record EvaluationDto(String kind, String tier, int validRuns, String runimalType) {

        static EvaluationDto of(EvaluationView v) {
            return new EvaluationDto(
                    v.kind(),
                    v.tier() == null ? null : v.tier().name(),
                    v.validRuns(),
                    v.runimalType());
        }
    }

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
     * @param nextCursor 다음 페이지 커서(size를 줬을 때만), 없으면 null
     */
    record BattleHistoryResponse(
            List<Entry> entries,
            int total,
            int wins,
            int losses,
            int selfNewRecords,
            Long nextCursor) {
        static BattleHistoryResponse of(BattleHistoryView view) {
            return new BattleHistoryResponse(
                    view.entries().stream().map(Entry::of).toList(),
                    view.total(),
                    view.wins(),
                    view.losses(),
                    view.selfNewRecords(),
                    view.nextCursor());
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
     * @param gapSeconds 기록 차이(본인 − 상대, 초, 음수면 본인이 빠름), 없으면 null
     * @param sourceLabel 유명인 기록 출처 표기, 없으면 null
     * @param placementRound 배치고사 대결이면 판 번호, 아니면 null(05-03 5.7.3.3 P6, J24-22)
     * @param opponentMemberId 친구 대결이고 상대가 탈퇴하지 않았으면 상대 회원 ID, 그 밖은 null(D-492)
     */
    record Entry(
            long battleId,
            String type,
            String opponentName,
            long distanceMeters,
            Verdict verdict,
            LocalDateTime playedAt,
            boolean newRecord,
            Long gapSeconds,
            String sourceLabel,
            Integer placementRound,
            Long opponentMemberId) {
        static Entry of(BattleHistoryView.Entry e) {
            return new Entry(
                    e.battleId(),
                    e.type().name(),
                    e.opponentName().value(),
                    e.distance().meters(),
                    e.verdict(),
                    e.playedAt(),
                    e.newRecord(),
                    seconds(e.gap()),
                    e.sourceLabel(),
                    null,
                    null);
        }
    }

    static Long seconds(Duration duration) {
        return duration == null ? null : duration.toSeconds();
    }
}
