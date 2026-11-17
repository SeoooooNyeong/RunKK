package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나와의 싸움·유명인 기록 대결(설계 6.1.6, srs.md 6.3 4.6, SQ-07). 종류마다 연산을 나눴다(D-36). 상대 표는 시작 정보에 실어 앱이
 * 보간하고(D-05), 앱이 3초 카운트다운 뒤 markStarted로 시작을 알린다(R-02).
 */
@Service
public class RecordBattleService {

    private static final Logger log = LoggerFactory.getLogger(RecordBattleService.class);

    /** 기록이 없는 나와의 싸움 거리의 후보 문구(FR-096). */
    static final String NO_RECORD_YET = "아직 기록이 없어요";

    private static final DateTimeFormatter SELF_LABEL = DateTimeFormatter.ofPattern("MM/dd의 나");

    /** 워치 상대 기록 순서: 거리 → 완주 시간 오름차순(D-377). */
    private static final Comparator<RecordTrack> TRACK_ORDER =
            Comparator.comparing(RecordTrack::distance).thenComparing(RecordTrack::finishTime);

    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final CelebrityRecordRepository celebrityRecordRepository;
    private final BattleRepository battleRepository;
    private final MatchQueue matchQueue;
    private final Clock clock;
    private final OpponentLooks opponentLooks;

    public RecordBattleService(
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            CelebrityRecordRepository celebrityRecordRepository,
            BattleRepository battleRepository,
            MatchQueue matchQueue,
            Clock clock,
            OpponentLooks opponentLooks) {
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.celebrityRecordRepository = celebrityRecordRepository;
        this.battleRepository = battleRepository;
        this.matchQueue = matchQueue;
        this.clock = clock;
        this.opponentLooks = opponentLooks;
    }

    /**
     * 3·5·10km·하프 본인 최고 기록. 없으면 "아직 기록이 없어요". 기록이 있는 후보는 그 기록을 달린 날의 내 모습을 싣고, 모습이 없는 옛 기록이면 지금
     * 모습으로 대신한다(설계 5.31 [임시:D-성-251]).
     */
    @Transactional(readOnly = true)
    public RecordBattleCandidates selfCandidates(long memberId) {
        List<RecordBattleCandidates.Candidate> candidates =
                Arrays.stream(StandardDistance.values())
                        .filter(StandardDistance::isSelfBattle)
                        .map(d -> selfCandidate(memberId, d))
                        .toList();
        return new RecordBattleCandidates(candidates);
    }

    private RecordBattleCandidates.Candidate selfCandidate(
            long memberId, StandardDistance distance) {
        Optional<BestEntry> best = runQueryService.best(memberId, distance);
        Duration time = best.flatMap(BestEntry::time).orElse(null);
        LocalDate on = best.map(BestEntry::achievedOn).orElse(null);
        String label = on == null ? NO_RECORD_YET : SELF_LABEL.format(on);
        return new RecordBattleCandidates.Candidate(
                null,
                label,
                distance.distance(),
                time,
                on,
                null,
                false,
                null,
                null,
                best.map(entry -> opponentLooks.selfLook(memberId, entry.recordId())).orElse(null));
    }

    /** 유명인 기록 목록과 출처, 거리 필터(FR-097, FR-100). 코치 기록만 보인다(D-851). */
    @Transactional(readOnly = true)
    public RecordBattleCandidates celebrityCandidates(
            long memberId, Optional<StandardDistance> filter) {
        List<RecordBattleCandidates.Candidate> candidates =
                celebrityRecordRepository.findAll(filter).stream()
                        .filter(CelebrityRecord::isCoach)
                        .map(
                                c ->
                                        new RecordBattleCandidates.Candidate(
                                                c.getId(),
                                                c.getAnonymousName(),
                                                c.getDistance(),
                                                c.getFinishTime(),
                                                null,
                                                c.getSource(),
                                                c.isEstimated(),
                                                myBest(memberId, c.getDistance()),
                                                c.figureKey().orElse(null)))
                        .toList();
        return new RecordBattleCandidates(candidates);
    }

    /**
     * 그 거리의 내 최고 기록(셀럽 카운트다운의 "첫 도전" 판정, v0.14 D-238). 기준 거리가 아니거나 기록이 없으면 null. 거리마다 장부를 한 번
     * 읽는다(후보는 거리 4종이라 많아야 네 번).
     */
    private Duration myBest(long memberId, Distance distance) {
        return Arrays.stream(StandardDistance.values())
                .filter(d -> d.distance().meters() == distance.meters())
                .findFirst()
                .flatMap(d -> runQueryService.best(memberId, d))
                .flatMap(BestEntry::time)
                .orElse(null);
    }

    /** d.isSelfBattle, 위치 동의 → 그 거리 최고 기록(없으면 NO_RECORD) → "MM/DD의 나" 상대로 SELF 대결. */
    @Transactional
    public RecordBattleStart startSelfBattle(long memberId, StandardDistance d) {
        if (!d.isSelfBattle()) {
            throw new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID);
        }
        requireLocationConsent(memberId);
        BestEntry best =
                runQueryService
                        .best(memberId, d)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.NO_RECORD));
        RunRecordOpponent self =
                selfOpponent(
                        best,
                        runQueryService.curve(best.recordId(), d.distance()),
                        tierOf(memberId));
        Battle battle = create(BattleType.SELF, memberId, d.distance(), self);
        return new RecordBattleStart(
                battle.getId(),
                BattleType.SELF,
                self.displayName(),
                battle.getDistance(),
                battle.getOpponentTrack().orElseThrow(),
                null,
                false,
                null,
                opponentLooks.selfLook(memberId, best.recordId()));
    }

    /** 나와의 싸움 상대: 그 거리 최고 기록의 1초 표본, 표시명 "MM/dd의 나"(POST /battles/self와 워치 미리 받기가 함께 쓴다). */
    private RunRecordOpponent selfOpponent(BestEntry best, RunCurve curve, Tier tier) {
        return new RunRecordOpponent(
                best.recordId(),
                runQueryService.comparisonOf(best.recordId()),
                curve,
                new RunRecordOpponent.Owner(
                        DisplayName.of(SELF_LABEL.format(best.achievedOn())), tier));
    }

    /** 위치 동의 → 유명인 기록(없으면 CELEBRITY_REMOVED) → CELEBRITY 대결. 대결 거리 = 기록 거리. */
    @Transactional
    public RecordBattleStart startCelebrityBattle(long memberId, long celebrityId) {
        requireLocationConsent(memberId);
        CelebrityRecord celebrity =
                celebrityRecordRepository
                        .findById(celebrityId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.CELEBRITY_REMOVED));
        Battle battle = create(BattleType.CELEBRITY, memberId, celebrity.getDistance(), celebrity);
        return new RecordBattleStart(
                battle.getId(),
                BattleType.CELEBRITY,
                celebrity.displayName(),
                battle.getDistance(),
                battle.getOpponentTrack().orElseThrow(),
                celebrity.getSource(),
                celebrity.isEstimated(),
                battle.getOpponentFigure().orElse(null),
                null);
    }

    /**
     * 워치가 미리 받아 두는 기록 대결 상대(v0.21 D-377, FR-260, SQ-46). 읽기만 하고 대결을 만들지 않는다. SELF: 나와의 싸움
     * 거리(1·3·5·10km·하프)마다 최고 기록이 있으면 하나. CELEBRITY: 등록된 유명인 기록 전부(후보 목록과 같은 범위). 상대 표는 POST
     * /battles/self·celebrity와 같은 OpponentRecord.track으로 만든다. 표를 만들 수 없는 기록(경로 파기·기록 없음, 대결 거리에 닿지
     * 않는 표)은 시작할 때도 거부되므로 뺀다. 거리 → 완주 시간 오름차순. 시작이 아니라 위치 동의는 보지 않는다. W31: CELEBRITY 목록의 track은 비어
     * 있고(목록만), 표는 celebrityTrack(id)으로 받는다. SELF는 표를 싣는다.
     *
     * @throws IllegalArgumentException kind가 SELF·CELEBRITY가 아님
     */
    @Transactional(readOnly = true)
    public List<RecordTrack> recordTracks(long memberId, BattleType kind) {
        List<RecordTrack> tracks =
                switch (kind) {
                    case SELF -> selfTracks(memberId);
                    case CELEBRITY -> celebrityTracks();
                    default ->
                            throw new IllegalArgumentException(
                                    "기록 상대 미리 받기는 SELF·CELEBRITY만 받는다: " + kind);
                };
        return tracks.stream().sorted(TRACK_ORDER).toList();
    }

    private List<RecordTrack> selfTracks(long memberId) {
        Map<StandardDistance, BestEntry> bests = new EnumMap<>(StandardDistance.class);
        for (StandardDistance d : StandardDistance.values()) {
            if (d.isSelfBattle()) {
                runQueryService
                        .best(memberId, d)
                        .filter(b -> b.time().isPresent())
                        .ifPresent(b -> bests.put(d, b));
            }
        }
        if (bests.isEmpty()) {
            return List.of();
        }
        Tier tier = tierOf(memberId);
        List<RecordTrack> tracks = new ArrayList<>();
        bests.forEach((d, best) -> selfTrack(best, d, tier).ifPresent(tracks::add));
        log.info(
                "record_tracks memberId={} kind=SELF returned={} skipped={}",
                memberId,
                tracks.size(),
                bests.size() - tracks.size());
        return tracks;
    }

    /** 경로 파기·기록 없음(POST /battles/self도 거부하는 기록)이거나 표가 거리에 닿지 않으면 비어 있다. */
    private Optional<RecordTrack> selfTrack(BestEntry best, StandardDistance d, Tier tier) {
        Optional<RunCurve> curve = runQueryService.curveOf(best.recordId(), d.distance());
        if (curve.isEmpty()) {
            log.debug(
                    "record_track_skipped recordId={} distance={} reason=no_curve",
                    best.recordId(),
                    d);
            return Optional.empty();
        }
        RunRecordOpponent opponent = selfOpponent(best, curve.get(), tier);
        return reachableTrack(opponent, d.distance())
                .map(
                        track ->
                                new RecordTrack(
                                        BattleType.SELF,
                                        null,
                                        opponent.displayName(),
                                        d.distance(),
                                        best.time().orElseThrow(),
                                        track,
                                        null,
                                        false,
                                        opponent.figureKey().orElse(null)));
    }

    /** W31: 목록은 표(track)를 계산하지 않는다. 표를 만들 수 없는 기록을 빼는 규칙만 가볍게 확인한다. */
    private List<RecordTrack> celebrityTracks() {
        List<CelebrityRecord> celebrities = celebrityRecordRepository.findAll(Optional.empty());
        List<RecordTrack> tracks =
                celebrities.stream()
                        .filter(CelebrityRecord::isCoach)
                        .filter(CelebrityRecord::hasReachableTrack)
                        .map(c -> celebrityEntry(c, OpponentTrack.empty()))
                        .toList();
        log.info(
                "record_tracks kind=CELEBRITY returned={} skipped={}",
                tracks.size(),
                celebrities.size() - tracks.size());
        return tracks;
    }

    /**
     * W31: 셀럽 하나의 기록과 상대 표(1초 1점, POST /battles/celebrity와 같은 표). 없거나 표를 만들 수 없으면(대결 거리에 닿지 않음) 비어
     * 있다. 읽기만 하고 대결을 만들지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<RecordTrack> celebrityTrack(long celebrityId) {
        return celebrityRecordRepository
                .findById(celebrityId)
                .flatMap(
                        c ->
                                reachableTrack(c, c.getDistance())
                                        .map(track -> celebrityEntry(c, track)));
    }

    private static RecordTrack celebrityEntry(CelebrityRecord c, OpponentTrack track) {
        return new RecordTrack(
                BattleType.CELEBRITY,
                c.getId(),
                c.displayName(),
                c.getDistance(),
                c.getFinishTime(),
                track,
                c.getSource(),
                c.isEstimated(),
                c.figureKey().orElse(null));
    }

    /** 상대 표가 대결 거리에 닿아야 한다(RecordBattleSpec.of와 같은 조건). 닿지 않으면 비어 있다. */
    private static Optional<OpponentTrack> reachableTrack(
            OpponentRecord opponent, Distance distance) {
        OpponentTrack track = opponent.track(distance);
        if (track.arrivalTime(distance).isEmpty()) {
            log.debug(
                    "record_track_skipped recordId={} distance={} reason=track_short",
                    opponent.recordId(),
                    distance.meters());
            return Optional.empty();
        }
        return Optional.of(track);
    }

    /**
     * 랜덤 매칭 대기 중이면 거부한다(ALREADY_IN_BATTLE, 코드 리뷰 2차 L10). 활성 대결이 있으면 거부하되, 시작 보고 전의 이전 기록 대결은 새 대결이
     * 대신한다(CANCELLED, 앱에서 카운트다운 전에 뒤로 가 다시 고른 경우). 회원별 활성 대결 1개는 active_member_id 고유 제약이 동시 생성까지
     * 지키고, 경합에서 진 요청도 ALREADY_IN_BATTLE이다(활성 대결 1개 불변식 결정).
     */
    private Battle create(
            BattleType type, long memberId, Distance distance, OpponentRecord opponent) {
        if (matchQueue.findWaiting(memberId).isPresent()) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        RecordBattleSpec spec = RecordBattleSpec.of(type, memberId, distance, opponent);
        try {
            for (Battle active : battleRepository.findActive(memberId)) {
                if (!active.cancelIfAwaitingStartReport(now)) {
                    throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
                }
                battleRepository.saveImmediately(active);
            }
            return battleRepository.saveImmediately(Battle.againstRecord(spec, now));
        } catch (OptimisticLockingFailureException e) {
            throw lostCreateRace(memberId, e); // 같은 이전 대결을 다른 요청이 먼저 대신했다
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.isUniqueViolation(e)) {
                throw e; // NOT NULL·길이 같은 다른 무결성 위반은 경합이 아니다(코드 리뷰 3차 B-L3)
            }
            throw lostCreateRace(memberId, e); // 활성 칸 고유 제약: 다른 생성 요청이 먼저 들어갔다
        }
    }

    private static BusinessRuleException lostCreateRace(long memberId, RuntimeException e) {
        log.warn(
                "record_battle_create_conflict memberId={} error={}",
                memberId,
                e.getClass().getSimpleName(),
                e);
        return new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
    }

    /** 앱의 3초 카운트다운이 끝났다는 보고 → Battle.start(now)(R-02). 기록 대결만, 같은 보고를 다시 받으면 무시한다. */
    @Transactional
    public void markStarted(long memberId, long battleId) {
        Battle battle =
                battleRepository
                        .findById(battleId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_NOT_FOUND));
        if (!battle.isParticipant(memberId)) {
            throw new PermissionDeniedException("대결 참가자가 아니다");
        }
        if (!battle.getType().startsWithCountdown()) {
            throw new IllegalArgumentException("시작 보고는 나와의 싸움·유명인 대결만 받는다");
        }
        if (battle.isCancelled()) {
            throw new BusinessRuleException(ReasonCode.BATTLE_NOT_FOUND); // 버려져 취소됐다
        }
        if (battle.getStartAt().isPresent()) {
            return; // 앱 재시도: 이미 시작했다(멱등)
        }
        battle.start(LocalDateTime.now(clock));
        battleRepository.save(battle);
    }

    private void requireLocationConsent(long memberId) {
        if (!memberQueryService.hasConsent(memberId, ConsentItem.LOCATION)) {
            throw new BusinessRuleException(ReasonCode.LOCATION_CONSENT_REQUIRED);
        }
    }

    private Tier tierOf(long memberId) {
        return Optional.ofNullable(memberQueryService.summaries(List.of(memberId)).get(memberId))
                .map(MemberSummary::tier)
                .orElse(Tier.UNRANKED);
    }
}
