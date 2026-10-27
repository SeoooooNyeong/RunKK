package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchStatus;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.battle.QueueRemoval;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.time.Duration;
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
    private static final Long DONE = 1L;

    /** 표 수명. 대기는 15초 뒤 고스트로 넘어가고 배정된 표는 대결 저장 직후 지우므로, 장애로 남은 표가 재요청을 영구히 막지 않게 둔다. */
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    /** 없을 때만 넣기(SETNX와 같은 뜻). KEYS[1] 표, KEYS[2] 정렬 집합. */
    private static final RedisScript<Long> ENQUEUE =
            new DefaultRedisScript<>(
                    "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                            + "redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'tier', ARGV[2],"
                            + " 'distance', ARGV[3], 'since', ARGV[4], 'status', 'WAITING') "
                            + "redis.call('EXPIRE', KEYS[1], ARGV[6])"
                            + " redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1]) return 1",
                    Long.class);

    private static final RedisScript<Long> REMOVE =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[2], ARGV[1]) return redis.call('DEL', KEYS[1])",
                    Long.class);

    /**
     * 취소(D-83): 상태 확인과 삭제를 한 스크립트로 한다. KEYS[1] 표, KEYS[2] 정렬 집합. 1 = WAITING이라 지움, 2 = 배정됨(지우지 않음),
     * 0 = 표 없음.
     */
    private static final RedisScript<Long> REMOVE_IF_WAITING =
            new DefaultRedisScript<>(
                    "local s = redis.call('HGET', KEYS[1], 'status')"
                            + " if not s then return 0 end"
                            + " if s ~= 'WAITING' then return 2 end"
                            + " redis.call('ZREM', KEYS[2], ARGV[1]) redis.call('DEL', KEYS[1])"
                            + " return 1",
                    Long.class);

    private static final long SCRIPT_REMOVED = 1;
    private static final long SCRIPT_ASSIGNED = 2;

    /** A-22: 두 표가 모두 WAITING일 때만 둘 다 ASSIGNED. */
    private static final RedisScript<Long> ASSIGN_PAIR =
            new DefaultRedisScript<>(
                    "if redis.call('HGET', KEYS[1], 'status') == 'WAITING'"
                            + " and redis.call('HGET', KEYS[2], 'status') == 'WAITING' then"
                            + " redis.call('HSET', KEYS[1], 'status', 'ASSIGNED')"
                            + " redis.call('HSET', KEYS[2], 'status', 'ASSIGNED') return 1 end"
                            + " return 0",
                    Long.class);

    /** D-64: 표가 WAITING일 때만 ASSIGNED(고스트 매칭 시작). */
    private static final RedisScript<Long> ASSIGN_ONE =
            new DefaultRedisScript<>(
                    "if redis.call('HGET', KEYS[1], 'status') == 'WAITING' then"
                            + " redis.call('HSET', KEYS[1], 'status', 'ASSIGNED') return 1 end"
                            + " return 0",
                    Long.class);

    /** D-64: 표가 ASSIGNED일 때만 WAITING으로 되돌린다(대기 시작 시각과 순서 항목은 그대로). */
    private static final RedisScript<Long> RELEASE =
            new DefaultRedisScript<>(
                    "if redis.call('HGET', KEYS[1], 'status') == 'ASSIGNED' then"
                            + " redis.call('HSET', KEYS[1], 'status', 'WAITING') return 1 end"
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
                        String.valueOf(score(ticket.getWaitingSince())),
                        String.valueOf(TICKET_TTL.toSeconds()));
        return DONE.equals(added);
    }

    @Override
    public boolean remove(long memberId) {
        Long removed =
                redis.execute(
                        REMOVE, List.of(ticketKey(memberId), QUEUE_KEY), String.valueOf(memberId));
        return removed != null && removed > 0;
    }

    @Override
    public QueueRemoval removeIfWaiting(long memberId) {
        Long result =
                redis.execute(
                        REMOVE_IF_WAITING,
                        List.of(ticketKey(memberId), QUEUE_KEY),
                        String.valueOf(memberId));
        if (result == null) {
            return QueueRemoval.ABSENT;
        }
        if (result == SCRIPT_REMOVED) {
            return QueueRemoval.REMOVED;
        }
        return result == SCRIPT_ASSIGNED ? QueueRemoval.ASSIGNED : QueueRemoval.ABSENT;
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
            Optional<MatchTicket> ticket = findWaiting(Long.parseLong(id));
            if (ticket.isEmpty()) {
                redis.opsForZSet().remove(QUEUE_KEY, id); // 수명이 다한 표의 순서 항목을 치운다
            }
            ticket.filter(found -> found.getStatus() == MatchStatus.WAITING)
                    .ifPresent(tickets::add);
        }
        return tickets;
    }

    @Override
    public boolean tryAssignPair(long a, long b) {
        Long assigned = redis.execute(ASSIGN_PAIR, List.of(ticketKey(a), ticketKey(b)));
        return DONE.equals(assigned);
    }

    @Override
    public boolean tryAssign(long memberId) {
        return DONE.equals(redis.execute(ASSIGN_ONE, List.of(ticketKey(memberId))));
    }

    @Override
    public void release(long memberId) {
        redis.execute(RELEASE, List.of(ticketKey(memberId)));
    }

    /** 대기 표로 열고 저장 상태가 ASSIGNED면 배정한다. */
    private static MatchTicket ticketOf(Map<Object, Object> fields) {
        MatchTicket ticket =
                MatchTicket.open(
                        Long.parseLong((String) fields.get(MEMBER)),
                        Tier.valueOf((String) fields.get(TIER)),
                        StandardDistance.valueOf((String) fields.get(DISTANCE)),
                        LocalDateTime.parse((String) fields.get(SINCE)));
        if (MatchStatus.valueOf((String) fields.get(STATUS)) == MatchStatus.ASSIGNED) {
            ticket.assign();
        }
        return ticket;
    }

    private static String ticketKey(long memberId) {
        return TICKET_PREFIX + memberId;
    }

    private static long score(LocalDateTime since) {
        return since.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
