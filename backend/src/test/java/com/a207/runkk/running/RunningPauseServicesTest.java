package com.a207.runkk.running;

import static com.a207.runkk.running.PauseJudgementTest.concat;
import static com.a207.runkk.running.PauseJudgementTest.free;
import static com.a207.runkk.running.PauseJudgementTest.leg;
import static com.a207.runkk.running.PauseJudgementTest.pause;
import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-26 서버 부분(TC-6676·TC-6678·TC-6682)과 S2의 새 조회 연산: 정지 구간을 받은 업로드가 정지를 저장하고 다섯째 조건(+1초 경계)으로 판정하며,
 * 상세에 정지 구간이 실리고 최고 기록은 정지를 뺀다. validRunsAfter·validDistanceOf·memberIdsSavedBetween(D-108, D-139).
 */
@DisplayName("러닝 정지 업로드·조회 연산(S2)")
class RunningPauseServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 19, 0);
    private static final LocalDateTime T10 = LocalDateTime.of(2026, 10, 22, 10, 0);

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final InMemoryRunningRepositories.Books books = new InMemoryRunningRepositories.Books();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final RunQueryService query = new RunQueryService(records, books, clock);

    private RunUploadService uploads() {
        return new RunUploadService(
                records,
                books,
                (point, at) -> Optional.empty(),
                new MemberQueryService(members),
                memberId -> java.util.Optional.empty(),
                published::add,
                NoTransactions.manager(),
                clock);
    }

    private long member() {
        return members.save(MemberFixtures.member("정지" + (members.size() + 10))).getId();
    }

    /** TC-6676: 10:00~10:30, 정지 [10:10, 10:15]·[10:20, 10:21], 운동 24:00, 5km(경로 5km). */
    private static RunUpload tc6676(String id, Duration elapsed) {
        double v = 5_000 / 1_440.0;
        List<LocationPoint> points =
                concat(
                        concat(
                                leg(T10, T10.plusMinutes(10), 0, v),
                                leg(T10.plusMinutes(15), T10.plusMinutes(20), 600 * v, v)),
                        leg(T10.plusMinutes(21), T10.plusMinutes(30), 900 * v, v));
        return free(
                id,
                T10,
                T10.plusMinutes(30),
                5_000,
                elapsed,
                points,
                List.of(
                        pause(T10.plusMinutes(10), T10.plusMinutes(15)),
                        pause(T10.plusMinutes(20), T10.plusMinutes(21))));
    }

    @Test
    @DisplayName("TC-6676·TC-6682: 정지 2개 저장(정지 합 6분), 경과 = 운동 24분, 상세 pauses 2개, 5km 최고 기록은 정지 제외")
    void tc6676_storesPausesAndDetailShowsThem() {
        long m = member();
        UploadResult result =
                uploads().upload(m, new RunUploadCommand(tc6676(id(1), Duration.ofMinutes(24))));
        assertThat(result.valid()).isTrue();

        RunRecord stored = records.findById(id(1)).orElseThrow();
        assertThat(stored.pauses()).hasSize(2);
        assertThat(stored.pausedTotal()).isEqualTo(Duration.ofMinutes(6));
        assertThat(stored.getElapsed()).isEqualTo(Duration.ofMinutes(24));

        RunDetail detail = query.detail(id(1), OptionalInt.empty());
        assertThat(detail.pauses())
                .extracting(PauseInterval::from)
                .containsExactly(T10.plusMinutes(10), T10.plusMinutes(20));
        assertThat(query.best(m, StandardDistance.KM5).orElseThrow().time())
                .hasValueSatisfying(t -> assertThat(t.toSeconds()).isBetween(1_438L, 1_441L));
        assertThat(query.timeToReach(id(1), Distance.ofKm(5)))
                .hasValueSatisfying(t -> assertThat(t.toSeconds()).isBetween(1_438L, 1_441L));
    }

    @Test
    @DisplayName("TC-6678: 벽시계 30 − 정지 6 = 24분에 운동 시간 24:01.000 유효, 24:01.001 무효(저장, 순번 0)")
    void tc6678_plusOneSecondBoundary() {
        long m = member();
        RunUploadService service = uploads();
        assertThat(
                        service.upload(
                                        m,
                                        new RunUploadCommand(
                                                tc6676(id(1), Duration.ofMillis(1_441_000))))
                                .valid())
                .isTrue();
        assertThat(
                        service.upload(
                                        m,
                                        new RunUploadCommand(
                                                tc6676(id(2), Duration.ofMillis(1_441_001))))
                                .valid())
                .isFalse();
        RunRecordSaved second = (RunRecordSaved) published.get(1);
        assertThat(second.valid()).isFalse();
        assertThat(second.validRunOrdinal()).isZero();
        assertThat(records.findById(id(2))).isPresent();
    }

    @Test
    @DisplayName("TC-6685: 정지 없는 업로드는 정지 0으로 저장된다")
    void tc6685_noPauses() {
        long m = member();
        uploads().upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));
        assertThat(records.findById(id(1)).orElseThrow().pausedTotal()).isZero();
        assertThat(query.detail(id(1), OptionalInt.empty()).pauses()).isEmpty();
    }

    @Test
    @DisplayName("validRunsAfter: 유효 순번 > ordinal, 저장 일시·기록 ID 순(무효 제외, 순번은 저장 순서)")
    void validRunsAfter_ordinalsInSaveOrder() {
        long m = member();
        long other = member();
        uploads().upload(m, new RunUploadCommand(upload(id(1), START, 5_000, 1_500)));
        clock = Clock.offset(clock, Duration.ofMinutes(1));
        uploads().upload(m, new RunUploadCommand(upload(id(2), START.plusHours(1), 900, 300)));
        clock = Clock.offset(clock, Duration.ofMinutes(1));
        uploads().upload(m, new RunUploadCommand(upload(id(3), START.plusHours(2), 3_000, 900)));
        clock = Clock.offset(clock, Duration.ofMinutes(1));
        uploads().upload(m, new RunUploadCommand(upload(id(4), START.plusHours(3), 10_000, 3_600)));
        uploads().upload(other, new RunUploadCommand(upload(id(5), START, 5_000, 1_500)));

        List<ValidRunScore> after = query.validRunsAfter(m, 1);
        assertThat(after).extracting(ValidRunScore::ordinal).containsExactly(2, 3);
        assertThat(after)
                .extracting(ValidRunScore::distance)
                .containsExactly(Distance.ofKm(3), Distance.ofKm(10));
        assertThat(after.get(0).score())
                .isEqualTo(records.findById(id(3)).orElseThrow().getScore().value());
        assertThat(query.validRunsAfter(m, 0)).hasSize(3);
        assertThat(query.validRunsAfter(m, 3)).isEmpty();
    }

    @Test
    @DisplayName("D-139: validDistanceOf는 누적 유효 거리, memberIdsSavedBetween은 [from, to) 저장 회원")
    void narrowQueriesForAvatar() {
        long m = member();
        long other = member();
        uploads().upload(m, new RunUploadCommand(upload(id(1), START, 5_000, 1_500)));
        uploads().upload(m, new RunUploadCommand(upload(id(2), START.plusHours(1), 900, 300)));
        clock = Clock.offset(clock, Duration.ofDays(1));
        uploads().upload(other, new RunUploadCommand(upload(id(3), START, 3_000, 900)));

        assertThat(query.validDistanceOf(m)).isEqualTo(Distance.ofKm(5));
        assertThat(query.validDistanceOf(member())).isEqualTo(Distance.ZERO);
        assertThat(query.memberIdsSavedBetween(NOW, NOW.plusMinutes(1))).containsExactly(m);
        assertThat(query.memberIdsSavedBetween(NOW, NOW.plusDays(2)))
                .containsExactlyInAnyOrder(m, other);
        assertThat(query.memberIdsSavedBetween(NOW.plusMinutes(1), NOW.plusDays(1))).isEmpty();
    }
}
