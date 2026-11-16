package com.a207.runkk.battle;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대결 이력(설계 6.1.6, srs.md 6.3 4.7, FR-101). 랜덤 상대 표시명은 MemberQueryService.summaries 한 번으로 붙이고(N+1
 * 방지, 6.5), 회원이 없으면 "탈퇴한 사용자"다(FR-021). 기록 대결은 대결에 남긴 상대 표시명을 쓴다(FR-172). 고스트는 랜덤으로 보인다(Q-02). 기록
 * 차이는 두 도달 시간의 차이고, 유명인 출처는 유명인 기록을 한 번에 읽어 붙인다(지워진 기록은 출처 없이). 진행 중인 대결 조회(active)도 같은 표시명 규칙으로
 * 여기서 한다.
 */
@Service
public class BattleHistoryService {

    private final BattleRepository battleRepository;
    private final MemberQueryService memberQueryService;
    private final CelebrityRecordRepository celebrityRecordRepository;
    private final OpponentLooks opponentLooks;

    public BattleHistoryService(
            BattleRepository battleRepository,
            MemberQueryService memberQueryService,
            CelebrityRecordRepository celebrityRecordRepository,
            OpponentLooks opponentLooks) {
        this.battleRepository = battleRepository;
        this.memberQueryService = memberQueryService;
        this.celebrityRecordRepository = celebrityRecordRepository;
        this.opponentLooks = opponentLooks;
    }

    /**
     * FINISHED·RESULT_PENDING 최신순 전부. 필터 RANDOM은 GHOST·FRIEND를 포함한다(BattleType.filterGroup, D-230).
     */
    @Transactional(readOnly = true)
    public BattleHistoryView history(long memberId, DateRange range, Optional<BattleType> filter) {
        return history(memberId, range, filter, PageCursor.all());
    }

    /**
     * 셀럽 기록마다 회원이 [from, to] 안에 처음 이긴 판정 시각과 표시명(celebrityIds 순서, event 이벤트 응모·코치 승리 포인트,
     * S15P31A207-275).
     */
    @Transactional(readOnly = true)
    public List<CelebrityChallenge> celebrityChallenges(
            long memberId, List<Long> celebrityIds, LocalDateTime from, LocalDateTime to) {
        Map<Long, LocalDateTime> wins =
                battleRepository.findFirstCelebrityWins(memberId, celebrityIds, from, to);
        return celebrityIds.stream()
                .map(
                        id ->
                                new CelebrityChallenge(
                                        id,
                                        celebrityRecordRepository
                                                .findById(id)
                                                .map(CelebrityRecord::getAnonymousName)
                                                .orElse(null),
                                        wins.get(id)))
                .toList();
    }

    /**
     * 셀럽 기록 celebrityIds 가운데 하나를 [from, to] 안에 이긴 회원과 기록마다의 첫 판정 시각(회원 ID, 기록 ID 순, event 이벤트 응모자
     * 명단, S15P31A207-275). 한 회원이 여러 기록을 이겼으면 기록마다 한 줄이다.
     */
    @Transactional(readOnly = true)
    public List<CelebrityWin> celebrityWinners(
            List<Long> celebrityIds, LocalDateTime from, LocalDateTime to) {
        return battleRepository.findCelebrityWinners(celebrityIds, from, to);
    }

    /** 셀럽 기록 ID별 표시명(가림 이름 열). 지워진 기록은 빠진다(event 이벤트 응모자 명단, S15P31A207-275). */
    @Transactional(readOnly = true)
    public Map<Long, String> celebrityNames(List<Long> celebrityIds) {
        Map<Long, String> names = new HashMap<>();
        for (Long id : celebrityIds) {
            celebrityRecordRepository
                    .findById(id)
                    .ifPresent(r -> names.put(id, r.getAnonymousName()));
        }
        return names;
    }

    /** 통계는 기간 전체로, 목록은 page만큼(B3 리뷰: 이력 페이지 나누기). */
    @Transactional(readOnly = true)
    public BattleHistoryView history(
            long memberId, DateRange range, Optional<BattleType> filter, PageCursor page) {
        List<Battle> battles =
                battleRepository.findHistory(memberId, range).stream()
                        .filter(b -> filter.isEmpty() || b.getType().filterGroup() == filter.get())
                        .toList();
        Set<Long> rivals =
                battles.stream()
                        .flatMap(b -> b.rivalOf(memberId).stream())
                        .map(BattleParticipant::getMemberId)
                        .collect(Collectors.toSet());
        Map<Long, MemberSummary> names = memberQueryService.summaries(rivals);
        Map<String, Source> sources = sourcesOf(battles);
        List<BattleHistoryView.Entry> entries =
                battles.stream().map(b -> entryOf(b, memberId, names, sources)).toList();
        PageCursor.Slice<BattleHistoryView.Entry> slice =
                page.slice(entries, BattleHistoryView.Entry::battleId);
        return new BattleHistoryView(
                entries.size(),
                count(entries, Verdict.WIN),
                count(entries, Verdict.LOSE),
                (int) entries.stream().filter(BattleHistoryView.Entry::newRecord).count(),
                slice.items(),
                slice.nextCursor());
    }

    /**
     * 기록 목록 행에 붙일 대결 결과(v0.14 D-233): FINISHED·RESULT_PENDING만, 취소된 대결과 없는 ID는 빠진다. type은 이력 행 종류다.
     * 저장소를 한 번 읽는다(N+1 없음).
     */
    @Transactional(readOnly = true)
    public Map<Long, BattleOutcome> outcomesOf(long memberId, Collection<Long> battleIds) {
        if (battleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BattleOutcome> outcomes = new HashMap<>();
        for (Battle battle : battleRepository.findAllById(battleIds)) {
            boolean finished = battle.getStatus() == BattleStatus.FINISHED;
            boolean pending = battle.getStatus() == BattleStatus.RESULT_PENDING;
            Optional<BattleParticipant> me = battle.participant(memberId);
            if ((!finished && !pending) || me.isEmpty()) {
                continue;
            }
            Verdict verdict = finished ? me.get().getVerdict().orElse(null) : null;
            outcomes.put(
                    battle.getId(),
                    new BattleOutcome(battle.getType().historyGroup(), verdict, pending));
        }
        return outcomes;
    }

    /**
     * 진행 중인 대결(BattleRepository.findActive, 회원당 많아야 1개). 없으면 비어 있다. 앱 재실행 때 그만두기 안내에 쓴다(배틀런 QA 7번).
     * 상대 표시명은 이력과 같은 규칙이고, 종류는 이력 분류가 아니라 BattleType 그대로다.
     */
    @Transactional(readOnly = true)
    public Optional<ActiveBattleView> active(long memberId) {
        return battleRepository.findActive(memberId).stream()
                .findFirst()
                .map(
                        battle ->
                                new ActiveBattleView(
                                        battle.getId(),
                                        battle.getType(),
                                        battle.getStatus(),
                                        battle.getDistance(),
                                        OpponentNames.of(
                                                battle, memberId, rivalNames(battle, memberId)),
                                        OpponentGenders.nameFor(battle, memberId),
                                        battle.getStartAt().orElse(null),
                                        battle.getOpponentFigure().orElse(null),
                                        BattleResultService.placementRoundOf(battle),
                                        opponentLooks.of(battle, memberId)));
    }

    private Map<Long, MemberSummary> rivalNames(Battle battle, long memberId) {
        return battle.rivalOf(memberId)
                .map(rival -> memberQueryService.summaries(List.of(rival.getMemberId())))
                .orElse(Map.of());
    }

    /** 유명인 대결이 있을 때만 유명인 기록을 한 번 읽는다(기록 ID 문자열 → 출처). */
    private Map<String, Source> sourcesOf(List<Battle> battles) {
        boolean any = battles.stream().anyMatch(b -> b.getType() == BattleType.CELEBRITY);
        Map<String, Source> sources = new HashMap<>();
        if (any) {
            celebrityRecordRepository
                    .findAll(Optional.empty())
                    .forEach(c -> sources.put(String.valueOf(c.getId()), c.getSource()));
        }
        return sources;
    }

    private static BattleHistoryView.Entry entryOf(
            Battle battle,
            long memberId,
            Map<Long, MemberSummary> names,
            Map<String, Source> sources) {
        BattleParticipant me = battle.participant(memberId).orElseThrow();
        Verdict verdict =
                battle.getStatus() == BattleStatus.FINISHED ? me.getVerdict().orElse(null) : null;
        String sourceLabel =
                battle.getType() == BattleType.CELEBRITY
                        ? battle.getOpponent()
                                .map(OpponentRef::recordId)
                                .map(sources::get)
                                .map(Source::label)
                                .orElse(null)
                        : null;
        return new BattleHistoryView.Entry(
                battle.getId(),
                battle.getCreatedAt(),
                battle.getType().historyGroup(),
                OpponentNames.of(battle, memberId, names),
                battle.getDistance(),
                verdict,
                battle.getType() == BattleType.SELF && verdict == Verdict.WIN && me.isNewRecord(),
                gapOf(battle, me).orElse(null),
                sourceLabel,
                BattleResultService.placementRoundOf(battle),
                friendOf(battle, memberId, names));
    }

    /** 친구 대결이고 상대가 탈퇴하지 않았으면 상대 회원 ID(D-492). 그 밖(기록 상대, 랜덤 실시간 상대, 탈퇴)은 null. */
    private static Long friendOf(Battle battle, long memberId, Map<Long, MemberSummary> names) {
        if (battle.getType() != BattleType.FRIEND) {
            return null;
        }
        return battle.rivalOf(memberId)
                .map(BattleParticipant::getMemberId)
                .filter(id -> isActive(names.get(id)))
                .orElse(null);
    }

    private static boolean isActive(MemberSummary summary) {
        return summary != null && !DisplayName.WITHDRAWN.equals(summary.displayName());
    }

    private static Optional<Duration> gapOf(Battle battle, BattleParticipant me) {
        Optional<Duration> theirs =
                battle.getOpponent()
                        .map(OpponentRef::arrivalElapsed)
                        .or(
                                () ->
                                        battle.rivalOf(me.getMemberId())
                                                .flatMap(BattleParticipant::getReachElapsed));
        return me.getReachElapsed().flatMap(mine -> theirs.map(mine::minus));
    }

    private static int count(List<BattleHistoryView.Entry> entries, Verdict verdict) {
        return (int) entries.stream().filter(e -> e.verdict() == verdict).count();
    }
}
