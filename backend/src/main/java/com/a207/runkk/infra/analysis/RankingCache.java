package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.RankEntry;
import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingPage;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.analysis.RankingSnapshot;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 최근 랭킹 스냅숏의 Redis 캐시(설계 6.1.13 infra.analysis.RankingCache, D-25, NFR-001). 조회마다 스냅숏 전체를 풀지 않도록 둘로
 * 나눈다(B5 리뷰): ranking:{종류}:{기간} = 집계 시각·판 번호·상위 N줄(JSON), ranking:{종류}:{기간}:{판}:subjects = 회원(크루)별
 * 줄과 바로 위까지 거리(해시). 판 번호는 집계 시각이라 재집계가 새 해시를 다 쓴 뒤 상위 키를 바꾸고, 이전 판 해시는 만료로 사라진다. 만료 25시간(다음 0시 재집계가
 * 덮어쓴다). Redis를 쓸 수 없으면 비어 있다고 보고 DB에서 읽는다(5.5 장애: Redis).
 */
@Component
public class RankingCache {

    static final Duration TTL = Duration.ofHours(25);
    private static final String PREFIX = "ranking:";

    /** 해시가 있는지 가리는 표지 필드(줄이 하나도 없는 스냅숏도 해시가 있게). */
    private static final String MARKER = "_";

    private static final Logger log = LoggerFactory.getLogger(RankingCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RankingCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 상위 줄 + subject 줄. 상위 키나 그 판의 해시가 없으면 비어 있다(DB에서 읽는다). */
    public Optional<RankingPage> page(
            RankingKind kind, AggregationPeriod period, Optional<Long> subjectId) {
        try {
            String json = redis.opsForValue().get(topKey(kind, period));
            if (json == null) {
                return Optional.empty();
            }
            Top top = objectMapper.readValue(json, Top.class);
            String subjects = subjectsKey(kind, period, top.version());
            Row mine = null;
            if (subjectId.isPresent()) {
                Object row = redis.opsForHash().get(subjects, String.valueOf(subjectId.get()));
                if (row == null
                        && !Boolean.TRUE.equals(redis.opsForHash().hasKey(subjects, MARKER))) {
                    return Optional.empty();
                }
                mine = row == null ? null : objectMapper.readValue((String) row, Row.class);
            }
            return Optional.of(
                    new RankingPage(
                            top.computedAt(),
                            top.rows().stream().map(Row::toEntry).toList(),
                            mine == null ? null : mine.toEntry(),
                            mine == null || mine.gap() == null
                                    ? null
                                    : Distance.ofMeters(mine.gap())));
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
            long version = snapshot.getComputedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
            String subjects = subjectsKey(snapshot.getKind(), snapshot.getPeriod(), version);
            Map<Long, Distance> gaps = snapshot.gapsToNext();
            Map<String, String> rows = new HashMap<>();
            rows.put(MARKER, "1");
            for (RankEntry e : snapshot.entries()) {
                Distance gap = gaps.get(e.subjectId());
                rows.put(
                        String.valueOf(e.subjectId()),
                        objectMapper.writeValueAsString(
                                Row.of(e, gap == null ? null : gap.meters())));
            }
            redis.opsForHash().putAll(subjects, rows);
            redis.expire(subjects, TTL);
            List<RankEntry> entries = snapshot.entries();
            Top top =
                    new Top(
                            snapshot.getComputedAt(),
                            version,
                            entries
                                    .subList(0, Math.min(RankingService.TOP, entries.size()))
                                    .stream()
                                    .map(e -> Row.of(e, null))
                                    .toList());
            String key = topKey(snapshot.getKind(), snapshot.getPeriod());
            String json = objectMapper.writeValueAsString(top);
            if (onlyIfAbsent) {
                redis.opsForValue().setIfAbsent(key, json, TTL);
            } else {
                redis.opsForValue().set(key, json, TTL);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("ranking_cache_unwritable error={}", e.getClass().getSimpleName());
        }
    }

    private static String topKey(RankingKind kind, AggregationPeriod period) {
        return PREFIX + kind + ":" + period;
    }

    private static String subjectsKey(RankingKind kind, AggregationPeriod period, long version) {
        return topKey(kind, period) + ":" + version + ":subjects";
    }

    /**
     * 상위 키 형식.
     *
     * @param computedAt 집계 시각
     * @param version 판 번호(회원별 해시 키)
     * @param rows 상위 줄
     */
    record Top(LocalDateTime computedAt, long version, List<Row> rows) {}

    /**
     * 캐시 줄.
     *
     * @param rank 순위
     * @param subjectId 회원 또는 크루 ID
     * @param name 표시명
     * @param tier 티어
     * @param meters 거리(미터)
     * @param gap 바로 위 순위까지 거리(미터), 1위거나 상위 키 줄이면 null
     */
    record Row(int rank, long subjectId, String name, Tier tier, long meters, Long gap) {

        static Row of(RankEntry e, Long gap) {
            return new Row(
                    e.rank(),
                    e.subjectId(),
                    e.displayName().value(),
                    e.tier(),
                    e.distance().meters(),
                    gap);
        }

        RankEntry toEntry() {
            return new RankEntry(
                    rank, subjectId, DisplayName.of(name), tier, Distance.ofMeters(meters));
        }
    }
}
