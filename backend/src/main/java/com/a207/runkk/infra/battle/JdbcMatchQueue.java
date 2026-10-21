package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchStatus;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * battle.MatchQueue의 PostgreSQL 대안(설계 D-07, 5.3). runkk.queue=jdbc일 때 쓴다. 중복 대기는 기본 키(member_id)로
 * 막고, 짝 배정은 두 행을 {@code SELECT … FOR UPDATE SKIP LOCKED}로 잠근 뒤 둘 다 WAITING일 때만 바꾼다(FR-082).
 * match_ticket 테이블만 쓴다.
 */
@Component
@ConditionalOnProperty(name = "runkk.queue", havingValue = "jdbc")
public class JdbcMatchQueue implements MatchQueue {

    private static final String COLUMNS = "member_id, tier, distance, waiting_since, status";

    private final JdbcTemplate jdbc;

    public JdbcMatchQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enqueue(MatchTicket ticket) {
        int inserted =
                jdbc.update(
                        "insert into match_ticket ("
                                + COLUMNS
                                + ") values (?, ?, ?, ?, ?) on conflict (member_id) do nothing",
                        ticket.getMemberId(),
                        ticket.getTier().name(),
                        ticket.getDistance().name(),
                        Timestamp.valueOf(ticket.getWaitingSince()),
                        ticket.getStatus().name());
        return inserted == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean remove(long memberId) {
        return jdbc.update("delete from match_ticket where member_id = ?", memberId) > 0;
    }

    @Override
    public Optional<MatchTicket> findWaiting(long memberId) {
        List<MatchTicket> found =
                jdbc.query(
                        "select " + COLUMNS + " from match_ticket where member_id = ?",
                        JdbcMatchQueue::ticketOf,
                        memberId);
        return found.stream().findFirst();
    }

    @Override
    public List<MatchTicket> waitingOldestFirst() {
        return jdbc.query(
                "select "
                        + COLUMNS
                        + " from match_ticket where status = 'WAITING' order by waiting_since,"
                        + " member_id",
                JdbcMatchQueue::ticketOf);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAssignPair(long a, long b) {
        List<Long> locked =
                jdbc.queryForList(
                        "select member_id from match_ticket where member_id in (?, ?) and status ="
                                + " 'WAITING' for update skip locked",
                        Long.class,
                        a,
                        b);
        if (locked.size() != 2) {
            return false;
        }
        jdbc.update("update match_ticket set status = 'ASSIGNED' where member_id in (?, ?)", a, b);
        return true;
    }

    private static MatchTicket ticketOf(ResultSet rs, int row) throws SQLException {
        return MatchTicket.restore(
                rs.getLong("member_id"),
                Tier.valueOf(rs.getString("tier")),
                StandardDistance.valueOf(rs.getString("distance")),
                rs.getTimestamp("waiting_since").toLocalDateTime(),
                MatchStatus.valueOf(rs.getString("status")));
    }
}
