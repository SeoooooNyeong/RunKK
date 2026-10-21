package com.a207.runkk.battle;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** battle 응용 서비스 단위 시험용 인메모리 저장소·가짜 메신저·가짜 트랜잭션(13.1 "테스트는 InMemory…Repository"). */
public final class InMemoryBattleStores {

    private InMemoryBattleStores() {}

    /** 트랜잭션 없이 바로 실행한다. */
    public static BattleTransactions transactions() {
        return new BattleTransactions(
                new PlatformTransactionManager() {
                    @Override
                    public TransactionStatus getTransaction(TransactionDefinition definition) {
                        return new SimpleTransactionStatus();
                    }

                    @Override
                    public void commit(TransactionStatus status) {}

                    @Override
                    public void rollback(TransactionStatus status) {}
                });
    }

    /** 대결 저장소. 저장 때 ID를 붙인다. */
    public static final class Battles implements BattleRepository {
        private final Map<Long, Battle> rows = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Battle save(Battle battle) {
            if (battle.getId() == null) {
                ReflectionTestUtils.setField(battle, "id", sequence.incrementAndGet());
            }
            rows.put(battle.getId(), battle);
            return battle;
        }

        @Override
        public Optional<Battle> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean existsActive(long memberId) {
            return rows.values().stream().anyMatch(b -> b.isActive() && b.isParticipant(memberId));
        }

        private List<Battle> where(Predicate<Battle> condition) {
            return rows.values().stream().filter(condition).toList();
        }

        @Override
        public List<Battle> findStartDue(LocalDateTime now) {
            return where(
                    b ->
                            b.getStatus() == BattleStatus.WAITING_START
                                    && b.getStartAt().map(s -> !s.isAfter(now)).orElse(false));
        }

        @Override
        public List<Battle> findCountdownDue(LocalDateTime now) {
            return where(b -> b.isCloseDue(now));
        }

        @Override
        public List<Battle> findOpponentArrivalDue(LocalDateTime now) {
            return where(b -> b.isOpponentArrivalDue(now));
        }

        @Override
        public List<Battle> findPendingExpired(LocalDateTime cutoff) {
            return where(
                    b ->
                            b.getStatus() == BattleStatus.RESULT_PENDING
                                    && !b.pendingSince().isAfter(cutoff));
        }

        @Override
        public List<Battle> findHistory(long memberId, DateRange range) {
            return where(
                            b ->
                                    b.isParticipant(memberId)
                                            && !b.isActive()
                                            && range.contains(b.getCreatedAt().toLocalDate()))
                    .stream()
                    .sorted(Comparator.comparing(Battle::getCreatedAt).reversed())
                    .toList();
        }

        @Override
        public WeeklyRecord weeklyRecord(long memberId, Week week) {
            int[] counts = new int[Verdict.values().length];
            where(b -> b.getStatus() == BattleStatus.FINISHED && week.contains(b.getCreatedAt()))
                    .forEach(
                            b ->
                                    b.participant(memberId)
                                            .flatMap(BattleParticipant::getVerdict)
                                            .ifPresent(v -> counts[v.ordinal()]++));
            return new WeeklyRecord(
                    counts[Verdict.WIN.ordinal()],
                    counts[Verdict.LOSE.ordinal()],
                    counts[Verdict.DRAW.ordinal()]);
        }

        public List<Battle> all() {
            return List.copyOf(rows.values());
        }
    }

    /** 대기열. */
    public static final class Queue implements MatchQueue {
        private final Map<Long, MatchTicket> tickets = new LinkedHashMap<>();
        private boolean broken;

        public void breakDown() {
            broken = true;
        }

        private void check() {
            if (broken) {
                throw new org.springframework.dao.DataAccessResourceFailureException("redis down");
            }
        }

        @Override
        public synchronized boolean enqueue(MatchTicket ticket) {
            check();
            return tickets.putIfAbsent(ticket.getMemberId(), ticket) == null;
        }

        @Override
        public synchronized boolean remove(long memberId) {
            check();
            return tickets.remove(memberId) != null;
        }

        @Override
        public synchronized Optional<MatchTicket> findWaiting(long memberId) {
            check();
            return Optional.ofNullable(tickets.get(memberId));
        }

        @Override
        public synchronized List<MatchTicket> waitingOldestFirst() {
            check();
            return tickets.values().stream()
                    .filter(t -> t.getStatus() == MatchStatus.WAITING)
                    .sorted(Comparator.comparing(MatchTicket::getWaitingSince))
                    .map(
                            t ->
                                    MatchTicket.open(
                                            t.getMemberId(),
                                            t.getTier(),
                                            t.getDistance(),
                                            t.getWaitingSince()))
                    .toList();
        }

        @Override
        public synchronized boolean tryAssignPair(long a, long b) {
            MatchTicket first = tickets.get(a);
            MatchTicket second = tickets.get(b);
            if (first == null
                    || second == null
                    || first.getStatus() != MatchStatus.WAITING
                    || second.getStatus() != MatchStatus.WAITING) {
                return false;
            }
            first.assign();
            second.assign();
            return true;
        }
    }

    /** 진행 중 세션. */
    public static final class Sessions implements LiveSessionStore {
        private final Map<Long, LiveBattleSession.Snapshot> rows = new LinkedHashMap<>();

        @Override
        public Optional<LiveBattleSession> load(long battleId) {
            return Optional.ofNullable(rows.get(battleId)).map(LiveBattleSession::restore);
        }

        @Override
        public void save(LiveBattleSession session) {
            rows.put(session.battleId(), session.snapshot());
        }

        @Override
        public void delete(long battleId) {
            rows.remove(battleId);
        }

        @Override
        public List<Long> activeBattleIds() {
            return List.copyOf(rows.keySet());
        }
    }

    /** 가상 기록. */
    public static final class Virtuals implements VirtualRecordRepository {
        private final List<VirtualRecord> rows = new ArrayList<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public List<VirtualRecord> findByTierAndDistance(Tier tier, StandardDistance distance) {
            return rows.stream()
                    .filter(r -> r.getTier() == tier && r.getDistance() == distance)
                    .toList();
        }

        @Override
        public long count() {
            return rows.size();
        }

        @Override
        public void replaceAll(List<VirtualRecord> records) {
            rows.clear();
            records.forEach(r -> ReflectionTestUtils.setField(r, "id", sequence.incrementAndGet()));
            rows.addAll(records);
        }

        @Override
        public boolean existsByNickname(String nickname) {
            return rows.stream().anyMatch(r -> r.getNickname().equals(nickname));
        }
    }

    /** 유명인 기록. */
    public static final class Celebrities implements CelebrityRecordRepository {
        private final Map<Long, CelebrityRecord> rows = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public List<CelebrityRecord> findAll(Optional<StandardDistance> filter) {
            return rows.values().stream()
                    .filter(c -> filter.map(d -> d.distance().equals(c.getDistance())).orElse(true))
                    .toList();
        }

        @Override
        public Optional<CelebrityRecord> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public CelebrityRecord save(CelebrityRecord record) {
            if (record.getId() == null) {
                ReflectionTestUtils.setField(record, "id", sequence.incrementAndGet());
            }
            rows.put(record.getId(), record);
            return record;
        }

        @Override
        public void delete(long id) {
            rows.remove(id);
        }
    }

    /** 보낸 메시지를 남기는 메신저. */
    public static final class Messenger implements BattleMessenger {
        private final List<BattleDelivery> sent = new ArrayList<>();

        @Override
        public synchronized void send(long memberId, BattleMessage m) {
            sent.add(new BattleDelivery(memberId, m));
        }

        @Override
        public synchronized void sendAll(Collection<Long> memberIds, BattleMessage m) {
            memberIds.forEach(id -> send(id, m));
        }

        public synchronized List<BattleDelivery> sent() {
            return List.copyOf(sent);
        }

        public synchronized List<BattleMessage> to(long memberId, String type) {
            return sent.stream()
                    .filter(d -> d.recipientId() == memberId && d.message().type().equals(type))
                    .map(BattleDelivery::message)
                    .toList();
        }

        public synchronized void clear() {
            sent.clear();
        }
    }
}
