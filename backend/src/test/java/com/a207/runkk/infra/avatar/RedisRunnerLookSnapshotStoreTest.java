package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.avatar.RunnerLook;
import com.a207.runkk.avatar.RunnerLookSnapshotStore;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.web.WebTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 스냅숏 단위 러너 모습 Redis 저장소(S15P31A207-300, PM 2026-11-14). 시험 프로필의 내장 Redis로 직렬화 왕복·키·만료를 확인하고(web
 * 시험과 같은 문맥을 함께 쓴다), Redis 장애는 가짜 템플릿으로 확인한다.
 */
@DisplayName("infra.avatar 스냅숏 러너 모습 Redis 저장소")
class RedisRunnerLookSnapshotStoreTest extends WebTestSupport {

    @Autowired RunnerLookSnapshotStore store;
    @Autowired StringRedisTemplate redis;

    @Test
    @DisplayName("직렬화 왕복: 얼굴 키(null 포함)·착용 부위 키·체형·러닝량이 그대로 돌아오고 없는 회원은 빠진다")
    void roundTrip() {
        String snapshot = "PERSONAL:WEEKLY:" + UUID.randomUUID();
        RunnerLook dressed =
                new RunnerLook(
                        "selfie:abc",
                        Map.of(
                                ItemSlot.TOP, "top_crew_tee",
                                ItemSlot.BOTTOM, "bottom_leggings",
                                ItemSlot.HAT, "hat_cap"),
                        2,
                        51);
        RunnerLook plain = new RunnerLook(null, Map.of(), 0, 0);

        store.save(snapshot, Map.of(1L, dressed, 2L, plain));
        Map<Long, RunnerLook> found = store.find(snapshot, List.of(1L, 2L, 3L));

        assertThat(found).containsOnlyKeys(1L, 2L);
        assertThat(found.get(1L)).isEqualTo(dressed);
        assertThat(found.get(1L).outfit())
                .containsExactly(
                        Map.entry(ItemSlot.TOP, "top_crew_tee"),
                        Map.entry(ItemSlot.BOTTOM, "bottom_leggings"),
                        Map.entry(ItemSlot.HAT, "hat_cap"));
        assertThat(found.get(2L)).isEqualTo(plain);
        assertThat(found.get(2L).faceKey()).isNull();
        assertThat(store.find("PERSONAL:WEEKLY:other", List.of(1L))).isEmpty();
    }

    @Test
    @DisplayName("키 ranking:look:{스냅숏}, 해시 필드는 회원 ID, 만료는 랭킹 캐시와 같은 25시간")
    void keyAndTtl() {
        String snapshot = "PERSONAL:MONTHLY:" + UUID.randomUUID();
        store.save(snapshot, Map.of(7L, new RunnerLook(null, Map.of(), 1, 30)));

        String key = "ranking:look:" + snapshot;
        assertThat(redis.opsForHash().keys(key)).containsExactly("7");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS))
                .isGreaterThan(Duration.ofHours(24).toSeconds())
                .isLessThanOrEqualTo(Duration.ofHours(25).toSeconds());
    }

    @Test
    @DisplayName("AR-90 저장은 HSET과 만료를 스크립트 한 번으로 보낸다: 따로 부르는 만료가 없어 만료만 실패해 만료 없는 키가 남는 일이 없다")
    void saveSetsFieldsAndExpiryAtomically() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisRunnerLookSnapshotStore atomic = new RedisRunnerLookSnapshotStore(template, json);

        atomic.save("PERSONAL:WEEKLY:1", Map.of(1L, new RunnerLook(null, Map.of(), 0, 0)));

        verify(template, times(1))
                .execute(
                        any(RedisScript.class),
                        eq(List.of("ranking:look:PERSONAL:WEEKLY:1")),
                        any(Object[].class));
        verify(template, never()).expire(any(), any(Duration.class));
        verify(template, never()).opsForHash();
    }

    @Test
    @DisplayName("AR-90 만료 없이 남아 있던 키도 저장하면 만료가 걸리고, 이미 있던 필드는 지우지 않는다")
    void saveAddsFieldsAndRefreshesExpiryOnExistingKey() {
        String snapshot = "PERSONAL:WEEKLY:" + UUID.randomUUID();
        String key = "ranking:look:" + snapshot;
        store.save(snapshot, Map.of(1L, new RunnerLook(null, Map.of(), 0, 0)));
        redis.persist(key);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isEqualTo(-1L);

        store.save(
                snapshot,
                Map.of(
                        2L,
                        new RunnerLook(null, Map.of(), 1, 5),
                        3L,
                        new RunnerLook(null, Map.of(), 2, 9)));

        assertThat(store.find(snapshot, List.of(1L, 2L, 3L))).containsOnlyKeys(1L, 2L, 3L);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS))
                .isGreaterThan(Duration.ofHours(24).toSeconds())
                .isLessThanOrEqualTo(Duration.ofHours(25).toSeconds());
    }

    @Test
    @DisplayName("깨진 값은 그 회원만 없는 것으로 본다(다시 읽어 덮는다)")
    void unreadableValueIsMissing() {
        String snapshot = "PERSONAL:WEEKLY:" + UUID.randomUUID();
        store.save(snapshot, Map.of(1L, new RunnerLook(null, Map.of(), 0, 0)));
        redis.opsForHash().put("ranking:look:" + snapshot, "2", "{broken");

        assertThat(store.find(snapshot, List.of(1L, 2L))).containsOnlyKeys(1L);
    }

    @Test
    @DisplayName("빈 입력(회원 없음·모습 없음)이면 Redis를 부르지 않는다")
    void emptyInputSkipsRedis() {
        StringRedisTemplate untouched = mock(StringRedisTemplate.class);
        RedisRunnerLookSnapshotStore idle = new RedisRunnerLookSnapshotStore(untouched, json);

        assertThat(idle.find("PERSONAL:WEEKLY:1", List.of())).isEmpty();
        idle.save("PERSONAL:WEEKLY:1", Map.of());

        verifyNoInteractions(untouched);
    }

    @Test
    @DisplayName("응답이 회원 수보다 짧으면(비정상 응답) 받은 칸만 읽고 나머지 회원은 없는 것으로 본다")
    @SuppressWarnings("unchecked")
    void shortReplyReadsOnlyWhatCame() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(template.<Object, Object>opsForHash()).thenReturn(hash);
        String saved =
                json.writeValueAsString(
                        new RedisRunnerLookSnapshotStore.Row(null, Map.of(), 1, 30));
        when(hash.multiGet(any(), any())).thenReturn(List.of(saved));
        RedisRunnerLookSnapshotStore shortReply = new RedisRunnerLookSnapshotStore(template, json);

        assertThat(shortReply.find("PERSONAL:WEEKLY:1", List.of(1L, 2L)))
                .containsOnlyKeys(1L)
                .containsEntry(1L, new RunnerLook(null, Map.of(), 1, 30));
    }

    @Test
    @DisplayName("Redis 장애: 읽기는 빈 맵, 쓰기는 예외 없이 넘어간다(RankingCache와 같은 규칙)")
    void redisFailureIsSwallowed() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForHash()).thenThrow(new RedisConnectionFailureException("down"));
        when(broken.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisRunnerLookSnapshotStore down = new RedisRunnerLookSnapshotStore(broken, json);

        assertThat(down.find("PERSONAL:WEEKLY:1", List.of(1L))).isEmpty();
        assertThatCode(
                        () ->
                                down.save(
                                        "PERSONAL:WEEKLY:1",
                                        Map.of(1L, new RunnerLook(null, Map.of(), 0, 0))))
                .doesNotThrowAnyException();
    }
}
