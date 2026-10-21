package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchStatus;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * battle.MatchQueue의 Redis 구현(설계 5.1.1 infra.battle, 5.3, A-22). 대기 표는 회원마다 해시
 * 하나(match:ticket:{id}), 순서는 대기 시작 시각을 점수로 한 정렬 집합(match:queue)이다. 넣기·지우기·짝 배정은 Lua 스크립트 하나로 원자적으로
 * 한다(FR-080, FR-082). runkk.queue=jdbc면 JdbcMatchQueue가 대신한다(D-07).
 */
@Component
@ConditionalOnProperty(name = "runkk.queue", havingValue = "redis", matchIfMissing = true)
public class RedisMatchQueue implements MatchQueue {

    static final String QUEUE_KEY = "match:queue";
    static final String TICKET_PREFIX = "match:ticket:";

    private static final String MEMBER = "memberId";
    private static final String TIER = "tier";
    private static final String DISTANCE = "distance";
    private static final String SINCE = "since";
    private static final String STATUS = "status";

    /** 없을 때만 넣기(SETNX와 같은 뜻). KEYS[1] 표, KEYS[2] 정렬 집합. */
    private static final RedisScript<Long> ENQUEUE =
            new DefaultRedisScript<>(
                    "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                            + "redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'tier', ARGV[2],"
                            + " 'distance', ARGV[3], 'since', ARGV[4], 'status', 'WAITING') "
                            + "redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1]) return 1",
                    Long.class);

    private static final RedisScript<Long> REMOVE =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[2], ARGV[1]) return redis.call('DEL', KEYS[1])",
                    Long.class);

    /** A-22: 두 표가 모두 WAITING일 때만 둘 다 ASSIGNED. */
    private static final RedisScript<Long> ASSIGN_PAIR =
            new DefaultRedisScript<>(
                    "if redis.call('HGET', KEYS[1], 'status') == 'WAITING'"
                            + " and redis.call('HGET', KEYS[2], 'status') == 'WAITING' then"
                            + " redis.call('HSET', KEYS[1], 'status', 'ASSIGNED')"
                            + " redis.call('HSET', KEYS[2], 'status', 'ASSIGNED') return 1 end"
                            + " return 0",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisMatchQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean enqueue(MatchTicket ticket) {
        String id = String.valueOf(ticket.getMemberId());
        Long added =
                redis.execute(
                        ENQUEUE,
                        List.of(ticketKey(ticket.getMemberId()), QUEUE_KEY),
                        id,
                        ticket.getTier().name(),
                        ticket.getDistance().name(),
                        ticket.getWaitingSince().toString(),
                        String.valueOf(score(ticket.getWaitingSince())));
        return Long.valueOf(1).equals(added);
    }

    @Override
    public boolean remove(long memberId) {
        Long removed =
                redis.execute(
                        REMOVE, List.of(ticketKey(memberId), QUEUE_KEY), String.valueOf(memberId));
        return removed != null && removed > 0;
    }

    @Override
    public Optional<MatchTicket> findWaiting(long memberId) {
        Map<Object, Object> fields = redis.opsForHash().entries(ticketKey(memberId));
        return fields.isEmpty() ? Optional.empty() : Optional.of(ticketOf(fields));
    }

    @Override
    public List<MatchTicket> waitingOldestFirst() {
        Set<String> ids = redis.opsForZSet().range(QUEUE_KEY, 0, -1);
        List<MatchTicket> tickets = new ArrayList<>();
        if (ids == null) {
            return tickets;
        }
        for (String id : ids) {
            findWaiting(Long.parseLong(id))
                    .filter(t -> t.getStatus() == MatchStatus.WAITING)
                    .ifPresent(tickets::add);
        }
        return tickets;
    }

    @Override
    public boolean tryAssignPair(long a, long b) {
        Long assigned = redis.execute(ASSIGN_PAIR, List.of(ticketKey(a), ticketKey(b)));
        return Long.valueOf(1).equals(assigned);
    }

    private static MatchTicket ticketOf(Map<Object, Object> fields) {
        return MatchTicket.restore(
                Long.parseLong((String) fields.get(MEMBER)),
                Tier.valueOf((String) fields.get(TIER)),
                StandardDistance.valueOf((String) fields.get(DISTANCE)),
                LocalDateTime.parse((String) fields.get(SINCE)),
                MatchStatus.valueOf((String) fields.get(STATUS)));
    }

    private static String ticketKey(long memberId) {
        return TICKET_PREFIX + memberId;
    }

    private static long score(LocalDateTime since) {
        return since.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
