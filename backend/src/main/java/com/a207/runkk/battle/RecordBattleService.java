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
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나와의 싸움·유명인 기록 대결(설계 6.1.6, srs.md 6.3 4.6, SQ-07). 종류마다 연산을 나눴다(D-36). 상대 표는 시작 정보에 실어 앱이
 * 보간하고(D-05), 앱이 3초 카운트다운 뒤 markStarted로 시작을 알린다(R-02).
 */
@Service
public class RecordBattleService {

    /** 기록이 없는 나와의 싸움 거리의 후보 문구(FR-096). */
    static final String NO_RECORD_YET = "아직 기록이 없어요";

    private static final DateTimeFormatter SELF_LABEL = DateTimeFormatter.ofPattern("MM/dd의 나");

    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final CelebrityRecordRepository celebrityRecordRepository;
    private final BattleRepository battleRepository;
    private final Clock clock;

    public RecordBattleService(
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            CelebrityRecordRepository celebrityRecordRepository,
            BattleRepository battleRepository,
            Clock clock) {
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.celebrityRecordRepository = celebrityRecordRepository;
        this.battleRepository = battleRepository;
        this.clock = clock;
    }

    /** 3·5·10km·하프 본인 최고 기록. 없으면 "아직 기록이 없어요". */
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
                null, label, distance.distance(), time, on, null, false);
    }

    /** 유명인 기록 목록과 출처, 거리 필터(FR-097, FR-100). */
    @Transactional(readOnly = true)
    public RecordBattleCandidates celebrityCandidates(
            long memberId, Optional<StandardDistance> filter) {
        List<RecordBattleCandidates.Candidate> candidates =
                celebrityRecordRepository.findAll(filter).stream()
                        .map(
                                c ->
                                        new RecordBattleCandidates.Candidate(
                                                c.getId(),
                                                c.getAnonymousName(),
                                                c.getDistance(),
                                                c.getFinishTime(),
                                                null,
                                                c.getSource(),
                                                c.isEstimated()))
                        .toList();
        return new RecordBattleCandidates(candidates);
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
                new RunRecordOpponent(
                        best.recordId(),
                        runQueryService.comparisonOf(best.recordId()),
                        runQueryService.curve(best.recordId(), d.distance()),
                        new RunRecordOpponent.Owner(
                                DisplayName.of(SELF_LABEL.format(best.achievedOn())),
                                tierOf(memberId)));
        Battle battle = create(BattleType.SELF, memberId, d.distance(), self);
        return new RecordBattleStart(
                battle.getId(),
                BattleType.SELF,
                self.displayName(),
                battle.getDistance(),
                battle.getOpponentTrack().orElseThrow(),
                null,
                false);
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
                celebrity.isEstimated());
    }

    private Battle create(
            BattleType type, long memberId, Distance distance, OpponentRecord opponent) {
        RecordBattleSpec spec = RecordBattleSpec.of(type, memberId, distance, opponent);
        return battleRepository.save(Battle.againstRecord(spec, LocalDateTime.now(clock)));
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
