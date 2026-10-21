package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.RankEntry;
import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingSnapshot;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 최근 랭킹 스냅숏의 Redis 캐시(설계 6.1.13 infra.analysis.RankingCache, D-25, NFR-001). 키 ranking:{종류}:{기간},
 * 값은 JSON, 만료 25시간(다음 0시 재집계가 덮어쓴다). Redis를 쓸 수 없으면 비어 있다고 보고 DB에서 읽는다(5.5 장애: Redis).
 */
@Component
public class RankingCache {

    static final Duration TTL = Duration.ofHours(25);
    private static final String PREFIX = "ranking:";
    private static final Logger log = LoggerFactory.getLogger(RankingCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RankingCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<RankingSnapshot> get(RankingKind kind, AggregationPeriod period) {
        try {
            String json = redis.opsForValue().get(key(kind, period));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Cached.class).toSnapshot());
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("ranking_cache_unreadable error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 새 스냅숏을 커밋한 뒤 덮어쓴다. */
    public void put(RankingSnapshot snapshot) {
        write(snapshot, false);
    }

    /** DB에서 읽은 값으로 빈 캐시를 채운다. 그 사이 재집계가 넣은 새 값을 덮지 않는다(리뷰 MEDIUM). */
    public void putIfAbsent(RankingSnapshot snapshot) {
        write(snapshot, true);
    }

    private void write(RankingSnapshot snapshot, boolean onlyIfAbsent) {
        try {
            String key = key(snapshot.getKind(), snapshot.getPeriod());
            String json = objectMapper.writeValueAsString(Cached.of(snapshot));
            if (onlyIfAbsent) {
                redis.opsForValue().setIfAbsent(key, json, TTL);
            } else {
                redis.opsForValue().set(key, json, TTL);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("ranking_cache_unwritable error={}", e.getClass().getSimpleName());
        }
    }

    private static String key(RankingKind kind, AggregationPeriod period) {
        return PREFIX + kind + ":" + period;
    }

    /**
     * 캐시 형식.
     *
     * @param kind 종류
     * @param period 기간
     * @param computedAt 집계 시각
     * @param rows 줄
     */
    record Cached(
            RankingKind kind, AggregationPeriod period, LocalDateTime computedAt, List<Row> rows) {

        static Cached of(RankingSnapshot s) {
            return new Cached(
                    s.getKind(),
                    s.getPeriod(),
                    s.getComputedAt(),
                    s.entries().stream()
                            .map(
                                    e ->
                                            new Row(
                                                    e.rank(),
                                                    e.subjectId(),
                                                    e.displayName().value(),
                                                    e.tier(),
                                                    e.distance().meters()))
                            .toList());
        }

        RankingSnapshot toSnapshot() {
            return RankingSnapshot.restore(
                    kind,
                    period,
                    computedAt,
                    rows.stream()
                            .map(
                                    r ->
                                            new RankEntry(
                                                    r.rank(),
                                                    r.subjectId(),
                                                    DisplayName.of(r.name()),
                                                    r.tier(),
                                                    Distance.ofMeters(r.meters())))
                            .toList());
        }
    }

    /**
     * 캐시 줄.
     *
     * @param rank 순위
     * @param subjectId 회원 또는 크루 ID
     * @param name 표시명
     * @param tier 티어
     * @param meters 거리(미터)
     */
    record Row(int rank, long subjectId, String name, Tier tier, long meters) {}
}
