package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.LiveBattleSession;
import com.a207.runkk.battle.LiveSessionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * battle.LiveSessionStore의 PostgreSQL 대안(설계 D-07). runkk.queue=jdbc일 때 쓴다(Redis 장기 장애, 5.5). 대결마다 행
 * 하나에 세션 상태를 JSON으로 둔다. live_battle_session 테이블만 쓴다.
 */
@Component
@ConditionalOnProperty(name = "runkk.queue", havingValue = "jdbc")
public class JdbcLiveSessionStore implements LiveSessionStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLiveSessionStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LiveBattleSession> load(long battleId) {
        List<String> found =
                jdbc.queryForList(
                        "select snapshot from live_battle_session where battle_id = ?",
                        String.class,
                        battleId);
        return found.stream().findFirst().map(this::restore);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(LiveBattleSession session) {
        LiveBattleSession.Snapshot snapshot = session.snapshot();
        String json = write(snapshot);
        int updated =
                jdbc.update(
                        "update live_battle_session set snapshot = ? where battle_id = ?",
                        json,
                        snapshot.battleId());
        if (updated == 0) {
            jdbc.update(
                    "insert into live_battle_session (battle_id, snapshot) values (?, ?)"
                            + " on conflict (battle_id) do nothing",
                    snapshot.battleId(),
                    json);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(long battleId) {
        jdbc.update("delete from live_battle_session where battle_id = ?", battleId);
    }

    @Override
    public List<Long> activeBattleIds() {
        return jdbc.queryForList(
                "select battle_id from live_battle_session order by battle_id", Long.class);
    }

    private LiveBattleSession restore(String json) {
        try {
            return LiveBattleSession.restore(
                    objectMapper.readValue(json, LiveBattleSession.Snapshot.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대결 세션을 읽지 못했다", e);
        }
    }

    private String write(LiveBattleSession.Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대결 세션을 직렬화하지 못했다", e);
        }
    }
}
