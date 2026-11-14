package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.springframework.data.redis.core.StringRedisTemplate;

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
    @DisplayName("깨진 값은 그 회원만 없는 것으로 본다(다시 읽어 덮는다)")
    void unreadableValueIsMissing() {
        String snapshot = "PERSONAL:WEEKLY:" + UUID.randomUUID();
        store.save(snapshot, Map.of(1L, new RunnerLook(null, Map.of(), 0, 0)));
        redis.opsForHash().put("ranking:look:" + snapshot, "2", "{broken");

        assertThat(store.find(snapshot, List.of(1L, 2L))).containsOnlyKeys(1L);
    }

    @Test
    @DisplayName("Redis 장애: 읽기는 빈 맵, 쓰기는 예외 없이 넘어간다(RankingCache와 같은 규칙)")
    void redisFailureIsSwallowed() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForHash()).thenThrow(new RedisConnectionFailureException("down"));
        when(broken.expire(any(), any(Duration.class)))
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
