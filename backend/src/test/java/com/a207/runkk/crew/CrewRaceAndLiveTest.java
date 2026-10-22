package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.live.CrewRunRelay;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 14장 순서 1: WeeklyRace.add(이중 합산 방지), CrewRunLiveSession(5.7.3 순위·3초 연결 확인),
 * TagOverlapRecommender(FR-118).
 */
@org.junit.jupiter.api.DisplayName("WeeklyRace·CrewRunLiveSession·TagOverlapRecommender")
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
        return CrewRunLiveSession.open(
                77,
                StandardDistance.KM1,
                Map.of(
                        1L,
                        DisplayName.of("새벽곰"),
                        2L,
                        DisplayName.of("달리는여우"),
                        3L,
                        DisplayName.of("한강토끼")),
                NOW);
    }

    private static ProgressReport at(long meters, long seconds) {
        return new ProgressReport(
                Distance.ofMeters(meters), Duration.ofSeconds(seconds), NOW.plusSeconds(seconds));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("진행값 → 모든 참가자에게 순위 중계(거리 큰 순), 위치 항목 없음, 참가자 아니면 무시")
    void live_relayRanking() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(300, 60), NOW.plusSeconds(60));
        List<?> out = s.onProgress(2, at(500, 60), NOW.plusSeconds(60));

        assertThat(out).hasSize(1);
        CrewRunRelay relay = (CrewRunRelay) out.get(0);
        assertThat(relay.postId()).isEqualTo(77);
        assertThat(relay.entries())
                .extracting(e -> e.name().value(), RelayEntry::rank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("달리는여우", 1),
                        org.assertj.core.groups.Tuple.tuple("새벽곰", 2),
                        org.assertj.core.groups.Tuple.tuple("한강토끼", 3));
        assertThat(s.onProgress(99, at(10, 1), NOW)).isEmpty();
        assertThat(s.participants()).containsExactlyInAnyOrder(1L, 2L, 3L);
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
    @org.junit.jupiter.api.DisplayName("도착한 참가자가 거리보다 앞, 그만둔 참가자는 순위 없이 QUIT, 3초 넘게 없으면 CHECKING")
    void live_arrivalQuitTimeout() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(1_000, 300), NOW.plusSeconds(300));
        s.onProgress(2, at(980, 300), NOW.plusSeconds(300));
        s.markQuit(3);

        List<RelayEntry> ranking = s.ranking();
        assertThat(ranking)
                .extracting(RelayEntry::connection)
                .containsExactly(
                        RelayEntry.Connection.ARRIVED,
                        RelayEntry.Connection.CONNECTED,
                        RelayEntry.Connection.QUIT);
        assertThat(ranking.get(2).rank()).isNull();

        assertThat(s.detectTimeouts(NOW.plusSeconds(303))).isEmpty();
        List<?> timeouts = s.detectTimeouts(NOW.plusSeconds(304));
        assertThat(timeouts).hasSize(1);
        assertThat(s.ranking().get(1).connection()).isEqualTo(RelayEntry.Connection.CHECKING);
        assertThat(s.detectTimeouts(NOW.plusSeconds(310))).as("이미 알렸다").isEmpty();
        s.onProgress(2, at(990, 311), NOW.plusSeconds(311));
        assertThat(s.ranking().get(1).connection()).isEqualTo(RelayEntry.Connection.CONNECTED);
        assertThat(s.onProgress(3, at(10, 312), NOW.plusSeconds(312))).as("그만둔 뒤 진행값").isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("스냅숏으로 되살려도 같은 순위다(Redis 세션)")
    void live_snapshotRoundTrip() {
        CrewRunLiveSession s = session();
        s.onProgress(1, at(400, 120), NOW.plusSeconds(120));
        s.markQuit(3);
        CrewRunLiveSession restored = CrewRunLiveSession.restore(s.snapshot());
        assertThat(restored.ranking()).isEqualTo(s.ranking());
        assertThat(restored.postId()).isEqualTo(77);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("추천: 겹치는 태그 1개 이상, 많은 순(같으면 최근 생성순), 상위 5개")
    void recommender() {
        TagSet runner =
                TagSet.of(
                        Set.of(
                                PersonalityTag.SOCIAL_RUN,
                                PersonalityTag.MORNING,
                                PersonalityTag.DIET),
                        TagOwner.RUNNER);
        List<CrewCandidate> candidates =
                List.of(
                        candidate(1, NOW, PersonalityTag.SOCIAL_RUN),
                        candidate(2, NOW.plusHours(1), PersonalityTag.SOCIAL_RUN),
                        candidate(3, NOW, PersonalityTag.SOCIAL_RUN, PersonalityTag.MORNING),
                        candidate(4, NOW, PersonalityTag.SOLO_RUN),
                        candidate(5, NOW, PersonalityTag.DIET),
                        candidate(6, NOW.minusDays(1), PersonalityTag.MORNING),
                        candidate(7, NOW.minusDays(2), PersonalityTag.DIET));

        List<Recommendation> picked = new TagOverlapRecommender().recommend(runner, candidates);

        assertThat(picked).extracting(r -> r.crew().crewId()).containsExactly(3L, 2L, 1L, 5L, 6L);
        assertThat(picked.get(0).matchCount()).isEqualTo(2);
    }

    private static CrewCandidate candidate(
            long id, LocalDateTime createdAt, PersonalityTag... tags) {
        return new CrewCandidate(
                id,
                "크루" + id,
                TagSet.of(Set.of(tags), TagOwner.CREW),
                3,
                JoinPolicy.FREE,
                createdAt);
    }
}
