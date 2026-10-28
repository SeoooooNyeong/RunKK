package com.a207.runkk.battle;

import com.a207.runkk.battle.live.BattleStart;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.battle.live.TrackPoint;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 성사 통지와 출발(설계 6.1.6, srs.md 6.3 4.3, SQ-03). 출발 = 성사 + 매칭 애니메이션 3초 + 10초(D-17). BattleStart는 출발
 * 예약 때 미리 보내고, 기록 상대면 상대 표를 싣는다(D-05). 고스트도 랜덤과 같은 MatchFound 항목만 보낸다(Q-02).
 */
@Service
public class BattleStartService {

    private static final Logger log = LoggerFactory.getLogger(BattleStartService.class);

    /** 매칭 애니메이션(D-17). */
    static final int ANIMATION_SECONDS = 3;

    /** 애니메이션 뒤 출발까지(FR-087). */
    static final Duration COUNTDOWN_LEAD = Duration.ofSeconds(10);

    /** 성사 뒤 출발까지 = 애니메이션 3초 + 10초(D-17, FR-087). */
    static final Duration START_DELAY = Duration.ofSeconds(ANIMATION_SECONDS).plus(COUNTDOWN_LEAD);

    private final BattleRepository battleRepository;
    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final BattleMessenger battleMessenger;
    private final LiveSessionStore liveSessionStore;
    private final Clock clock;
    private final BattleTransactions transactions;

    public BattleStartService(
            BattleRepository battleRepository,
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            BattleMessenger battleMessenger,
            LiveSessionStore liveSessionStore,
            Clock clock,
            BattleTransactions transactions) {
        this.battleRepository = battleRepository;
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.battleMessenger = battleMessenger;
        this.liveSessionStore = liveSessionStore;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** 양쪽 최고 기록을 붙여 MatchFound → 출발 예약(now + 13초) → BattleStart를 미리 보낸다. */
    @Transactional
    public void announceMatch(Battle b) {
        LocalDateTime now = LocalDateTime.now(clock);
        b.scheduleStart(now.plus(START_DELAY));
        Battle battle = battleRepository.save(b);
        Map<Long, MemberSummary> members = memberQueryService.summaries(battle.memberIds());
        Map<Long, MatchFound> found = new LinkedHashMap<>();
        battle.memberIds().forEach(id -> found.put(id, matchFoundFor(battle, id, members)));
        BattleStart start = startMessage(battle);
        List<Long> recipients = battle.memberIds();
        AfterCommit.run(
                () -> {
                    found.forEach(battleMessenger::send);
                    battleMessenger.sendAll(recipients, start);
                });
    }

    private MatchFound matchFoundFor(
            Battle battle, long memberId, Map<Long, MemberSummary> members) {
        Optional<StandardDistance> standard = battle.standardDistance();
        Duration myBest = standard.flatMap(each -> bestTime(memberId, each)).orElse(null);
        Optional<OpponentRef> record = battle.getOpponent();
        if (record.isPresent()) {
            OpponentRef ref = record.get();
            return new MatchFound(
                    battle.getId(),
                    ref.displayName(),
                    ref.displayTier(),
                    battle.getDistance(),
                    myBest,
                    ref.arrivalElapsed(),
                    ANIMATION_SECONDS);
        }
        long rivalId = battle.rivalOf(memberId).orElseThrow().getMemberId();
        MemberSummary rival = members.get(rivalId);
        return new MatchFound(
                battle.getId(),
                rival == null ? DisplayName.WITHDRAWN : rival.displayName(),
                rival == null ? Tier.UNRANKED : rival.tier(),
                battle.getDistance(),
                myBest,
                standard.flatMap(each -> bestTime(rivalId, each)).orElse(null),
                ANIMATION_SECONDS);
    }

    private Optional<Duration> bestTime(long memberId, StandardDistance distance) {
        return runQueryService.best(memberId, distance).flatMap(BestEntry::time);
    }

    /**
     * 재연결한 회원에게 출발 예약된 랜덤·고스트 활성 대결의 BattleStart를 다시 보낸다(배틀런 QA 1·2번). 출발 예약 때 미리 보낸 BattleStart는
     * 앱이 다시 연결하는 사이 끊기던 연결로 나가 사라질 수 있다(보내기가 실패해야 우편함에 남는다). 앱은 같은 BattleStart를 한 번만 쓴다.
     * realtime.LiveSocketHandler가 연결 때 우편함을 비운 뒤 부른다.
     */
    @Transactional(readOnly = true)
    public void resendStart(long memberId) {
        for (Battle battle : battleRepository.findActive(memberId)) {
            if (!battle.getType().startsWithCountdown() && battle.getStartAt().isPresent()) {
                battleMessenger.send(memberId, startMessage(battle));
            }
        }
    }

    /** 기록 상대면 상대 표(초·미터), 랜덤이면 null. */
    static BattleStart startMessage(Battle battle) {
        List<TrackPoint> track =
                battle.getOpponentTrack().map(BattleStartService::trackPoints).orElse(null);
        return new BattleStart(battle.getId(), battle.getStartAt().orElseThrow(), track);
    }

    /** 앱 TrackTable 형식(경과 초, 누적 미터). */
    static List<TrackPoint> trackPoints(OpponentTrack track) {
        return track.wholeSeconds().stream()
                .map(
                        sample ->
                                new TrackPoint(
                                        sample.elapsed().toSeconds(), sample.distance().meters()))
                .toList();
    }

    /** startAt이 지난 WAITING_START 대결을 시작한다. 대결마다 새 트랜잭션이고, 랜덤이면 커밋 뒤 실시간 세션을 연다. 시작한 대결 수. */
    public int startDue(LocalDateTime now) {
        int started = 0;
        for (Battle due : battleRepository.findStartDue(now)) {
            if (transactions.once("battle_start", () -> start(due.getId(), now))) {
                started++;
            }
        }
        return started;
    }

    private void start(long battleId, LocalDateTime now) {
        Battle battle = battleRepository.findById(battleId).orElseThrow();
        battle.start(now);
        battleRepository.save(battle);
        if (battle.getType() == BattleType.RANDOM) {
            LiveBattleSession session = openSession(battle, now);
            AfterCommit.run(() -> liveSessionStore.save(session));
        }
    }

    /**
     * 출발하지 못한 채 ABANDON_AFTER(10분)가 지난 대결을 CANCELLED로 끝낸다(활성 대결 1개 불변식 결정): 시작 보고 없이 버려진 나와의 싸움·유명인
     * 대결, 출발 시각이 지나도 시작하지 못한 랜덤·고스트 대결. 그래야 다음 대결 생성·매칭·탈퇴를 막지 않는다. batch.battle의
     * AbandonedBattleJob이 1분마다 부른다. 대결마다 새 트랜잭션이고 취소한 수를 돌려준다. 시작 보고가 없는 기록 대결은 판정할 수 없어(도착 시각을
     * 모른다) 잃는 결과가 없다. 실시간 세션은 출발 때 열리므로 지울 것이 없다.
     */
    public int cancelAbandoned(LocalDateTime now) {
        int cancelled = 0;
        for (Battle due : battleRepository.findAbandoned(now.minus(Battle.ABANDON_AFTER))) {
            if (transactions.once("battle_abandon", () -> cancel(due.getId(), now))) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private void cancel(long battleId, LocalDateTime now) {
        Battle battle = battleRepository.findById(battleId).orElseThrow();
        if (!battle.isAbandoned(now)) {
            return; // 그사이 시작 보고가 왔다
        }
        battle.cancelUnstarted(now);
        battleRepository.save(battle);
        log.info(
                "battle_abandoned battleId={} type={} memberIds={}",
                battleId,
                battle.getType(),
                battle.memberIds());
    }

    private LiveBattleSession openSession(Battle battle, LocalDateTime now) {
        Map<Long, MemberSummary> members = memberQueryService.summaries(battle.memberIds());
        Map<Long, DisplayName> names = new LinkedHashMap<>();
        for (long memberId : battle.memberIds()) {
            MemberSummary summary = members.get(memberId);
            names.put(memberId, summary == null ? DisplayName.WITHDRAWN : summary.displayName());
        }
        return LiveBattleSession.open(battle.getId(), battle.getDistance(), names, now);
    }
}
