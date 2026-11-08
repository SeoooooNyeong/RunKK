package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.live.CrewRunRelay;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 14장 순서 1: WeeklyRace.add(이중 합산 방지), CrewRunLiveSession(합산 방식 중계·일시정지·끝냄·3초 연결 확인). */
@org.junit.jupiter.api.DisplayName("WeeklyRace·CrewRunLiveSession")
class CrewRaceAndLiveTest {

    private static final Week WEEK = Week.of(NOW);

    @Test
    @org.junit.jupiter.api.DisplayName("WeeklyRace.add: 같은 기록은 한 번만, 기여율은 버림, 합계 0이면 0")
    void weeklyRace_add() {
        WeeklyRace race = WeeklyRace.empty(1, WEEK);
        assertThat(race.contributionRate(7)).isZero();

        assertThat(race.add(7, "r1", Distance.ofMeters(2_000))).isTrue();
        assertThat(race.add(7, "r1", Distance.ofMeters(2_000))).isFalse();
        assertThat(race.add(8, "r2", Distance.ofMeters(1_000))).isTrue();

        assertThat(race.total()).isEqualTo(Distance.ofMeters(3_000));
        assertThat(race.contributionRate(7)).isEqualTo(66);
        assertThat(race.contributionRate(8)).isEqualTo(33);
        assertThat(race.contributionOf("r1")).isEqualTo(Distance.ofMeters(2_000));
        assertThat(race.contributionOf("none")).isEqualTo(Distance.ZERO);
        assertThat(race.closesAt()).isEqualTo(WEEK.closesAt());
        assertThat(race.contributions())
                .extracting(RaceContribution::memberId)
                .containsExactlyInAnyOrder(7L, 8L);
    }

    private static CrewRunLiveSession session() {
        Map<Long, DisplayName> names = new LinkedHashMap<>();
        names.put(1L, DisplayName.of("새벽곰"));
        names.put(2L, DisplayName.of("달리는여우"));
        names.put(3L, DisplayName.of("한강토끼"));
        return CrewRunLiveSession.open(77, names, NOW);
    }

    private static ProgressReport at(long meters, long seconds) {
        return new ProgressReport(
                Distance.ofMeters(meters), Duration.ofSeconds(seconds), NOW.plusSeconds(seconds));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("진행값 → 모든 참가자에게 중계(입장 순, 순위 없음), 크루 거리 합, 참가자 아니면 무시")
    void live_relaySum() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(300, 60), NOW.plusSeconds(60));
        List<?> out = s.onProgress(2, at(500, 60), NOW.plusSeconds(60));

        assertThat(out).hasSize(1);
        CrewRunRelay relay = (CrewRunRelay) out.get(0);
        assertThat(relay.postId()).isEqualTo(77);
        assertThat(relay.totalDistance()).isEqualTo(Distance.ofMeters(800));
        assertThat(relay.entries())
                .extracting(e -> e.name().value(), e -> e.distance().meters(), RelayEntry::state)
                .containsExactly(
                        tuple("새벽곰", 300L, RelayEntry.State.RUNNING),
                        tuple("달리는여우", 500L, RelayEntry.State.RUNNING),
                        tuple("한강토끼", 0L, RelayEntry.State.RUNNING));
        assertThat(s.onProgress(99, at(10, 1), NOW)).isEmpty();
        assertThat(s.participants()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("늦게 온 이전 진행값(더 작은 거리)은 거리를 되돌리지 않고, 중계에 memberId가 있다")
    void live_ignoresRegression() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(600, 120), NOW.plusSeconds(120));
        CrewRunRelay relay =
                (CrewRunRelay) s.onProgress(1, at(400, 80), NOW.plusSeconds(121)).get(0);
        RelayEntry first = relay.entries().get(0);
        assertThat(first.memberId()).isEqualTo(1L);
        assertThat(first.distance()).isEqualTo(Distance.ofMeters(600));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "일시정지는 PAUSED, 정지 때보다 큰 경과가 오면 RUNNING, 멈춘 사람은 연결 확인으로 바꾸지 않는다")
    void live_pauseAndImplicitResume() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(500, 120), NOW.plusSeconds(120));
        s.onProgress(2, at(400, 120), NOW.plusSeconds(120));
        s.onProgress(3, at(300, 120), NOW.plusSeconds(120));
        assertThat(s.markPaused(1, Duration.ofSeconds(121))).isTrue();
        assertThat(s.entries().get(0).state()).isEqualTo(RelayEntry.State.PAUSED);

        // 정지 직전에 보낸 값(경과가 정지 때 이하)은 재개가 아니다
        s.onProgress(1, at(505, 121), NOW.plusSeconds(122));
        assertThat(s.entries().get(0).state()).isEqualTo(RelayEntry.State.PAUSED);

        s.detectTimeouts(NOW.plusSeconds(200));
        assertThat(s.entries())
                .extracting(RelayEntry::state)
                .containsExactly(
                        RelayEntry.State.PAUSED,
                        RelayEntry.State.CHECKING,
                        RelayEntry.State.CHECKING);

        s.onProgress(1, at(510, 122), NOW.plusSeconds(201));
        assertThat(s.entries().get(0).state()).isEqualTo(RelayEntry.State.RUNNING);
        assertThat(s.markPaused(99, null)).as("참가자 아님").isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("끝냄은 ENDED로 마지막 값을 남기고, 끝낸 뒤 진행값·정지는 무시, 거리 합에는 남는다")
    void live_endAndTimeout() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(1_000, 300), NOW.plusSeconds(300));
        s.onProgress(2, at(980, 300), NOW.plusSeconds(300));
        assertThat(
                        s.markEnded(
                                3,
                                new LiveProgress(
                                        Distance.ofMeters(700),
                                        Duration.ofSeconds(290),
                                        NOW.plusSeconds(300))))
                .isTrue();

        assertThat(s.entries())
                .extracting(RelayEntry::state)
                .containsExactly(
                        RelayEntry.State.RUNNING, RelayEntry.State.RUNNING, RelayEntry.State.ENDED);
        assertThat(s.totalDistance()).isEqualTo(Distance.ofMeters(2_680));
        assertThat(s.latestOf(3)).map(p -> p.distance().meters()).contains(700L);

        assertThat(s.detectTimeouts(NOW.plusSeconds(303))).isEmpty();
        List<?> timeouts = s.detectTimeouts(NOW.plusSeconds(304));
        assertThat(timeouts).hasSize(1);
        assertThat(s.entries().get(1).state()).isEqualTo(RelayEntry.State.CHECKING);
        assertThat(s.entries().get(2).state())
                .as("끝낸 사람은 연결 확인 안 함")
                .isEqualTo(RelayEntry.State.ENDED);
        assertThat(s.detectTimeouts(NOW.plusSeconds(310))).as("이미 알렸다").isEmpty();
        s.onProgress(2, at(990, 311), NOW.plusSeconds(311));
        assertThat(s.entries().get(1).state()).isEqualTo(RelayEntry.State.RUNNING);
        assertThat(s.onProgress(3, at(10, 312), NOW.plusSeconds(312))).as("끝낸 뒤 진행값").isEmpty();
        assertThat(s.markPaused(3, null)).as("끝낸 뒤 정지").isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("스냅숏으로 되살려도 같은 줄(입장 순)·상태다(Redis 세션)")
    void live_snapshotRoundTrip() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(400, 120), NOW.plusSeconds(120));
        s.markPaused(2, Duration.ofSeconds(60));
        s.markEnded(3, null);
        CrewRunLiveSession restored = CrewRunLiveSession.restore(s.snapshot());
        assertThat(restored.entries()).isEqualTo(s.entries());
        assertThat(restored.totalDistance()).isEqualTo(s.totalDistance());
        assertThat(restored.postId()).isEqualTo(77);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("받는 사람마다 본인 줄에만 mine 참")
    void relay_forRecipient() {
        CrewRunLiveSession s = session();
        CrewRunRelay relay = ((CrewRunRelay) s.relay()).forRecipient(2);
        assertThat(relay.entries())
                .extracting(RelayEntry::mine)
                .containsExactly(false, true, false);
    }
}
