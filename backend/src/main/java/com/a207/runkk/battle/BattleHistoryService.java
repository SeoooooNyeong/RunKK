package com.a207.runkk.battle;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import java.time.Duration;
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
 * 차이는 두 도달 시간의 차이고, 유명인 출처는 유명인 기록을 한 번에 읽어 붙인다(지워진 기록은 출처 없이).
 */
@Service
public class BattleHistoryService {

    private final BattleRepository battleRepository;
    private final MemberQueryService memberQueryService;
    private final CelebrityRecordRepository celebrityRecordRepository;

    public BattleHistoryService(
            BattleRepository battleRepository,
            MemberQueryService memberQueryService,
            CelebrityRecordRepository celebrityRecordRepository) {
        this.battleRepository = battleRepository;
        this.memberQueryService = memberQueryService;
        this.celebrityRecordRepository = celebrityRecordRepository;
    }

    /** FINISHED·RESULT_PENDING 최신순. 필터 RANDOM은 GHOST를 포함한다(BattleType.historyGroup). */
    @Transactional(readOnly = true)
    public BattleHistoryView history(long memberId, DateRange range, Optional<BattleType> filter) {
        List<Battle> battles =
                battleRepository.findHistory(memberId, range).stream()
                        .filter(b -> filter.isEmpty() || b.getType().historyGroup() == filter.get())
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
        return new BattleHistoryView(
                entries.size(),
                count(entries, Verdict.WIN),
                count(entries, Verdict.LOSE),
                (int) entries.stream().filter(BattleHistoryView.Entry::newRecord).count(),
                entries);
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
                sourceLabel);
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
