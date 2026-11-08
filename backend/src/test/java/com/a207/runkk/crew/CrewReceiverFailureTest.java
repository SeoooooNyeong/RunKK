package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.Distance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 코드 리뷰 2차 M4: 크루 AFTER_COMMIT 수신자(주간 거리 합산, 크루런 러닝 연결)는 일시 오류면 다시 하고, 계속 실패해도 예외를 던지지 않는다(유실분은 주간
 * 거리는 0시 재합산이 다시 맞춘다).
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("crew 사건 수신자 실패 처리(M4)")
class CrewReceiverFailureTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean WeeklyRaceRepository weeklyRaces;
    @MockitoSpyBean CrewPostRepository crewPosts;
    @Autowired WeeklyRaceService race;
    @Autowired CrewRunService crewRuns;
    @Autowired CrewService crews;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void restore() {
        reset(weeklyRaces, crewPosts);
    }

    private long crewLedBy(long leader) {
        return crews.create(
                        leader,
                        new CrewCreateCommand(
                                "수신크루" + NAMES.incrementAndGet(),
                                null,
                                Set.of(CrewTag.WEEKDAY_EVENING),
                                CrewVisibility.PUBLIC,
                                JoinPolicy.FREE,
                                "https://cdn/logo.png"))
                .crewId();
    }

    private static RunRecordSaved saved(long memberId, Long crewRunPostId, RunMode mode) {
        LocalDateTime start = LocalDateTime.now().plusSeconds(1).withNano(0); // 가입 시각 뒤
        return new RunRecordSaved(
                UUID.randomUUID().toString(),
                memberId,
                start,
                Distance.ofMeters(3_000),
                Duration.ofMinutes(18),
                true,
                40.0,
                mode,
                null,
                crewRunPostId,
                true,
                1,
                start);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("주간 거리 합산이 잠금 대기 오류를 한 번 받으면 다시 해서 합산한다")
    void weeklyRace_retriesTransientFailure() {
        long leader =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        "수신러너" + NAMES.incrementAndGet()))
                                        .getId());
        long crewId = crewLedBy(leader);
        doThrow(new CannotAcquireLockException("lock wait"))
                .doCallRealMethod()
                .when(weeklyRaces)
                .addAtomically(any(), any());

        assertThatCode(() -> race.onRunSaved(saved(leader, null, RunMode.FREE)))
                .doesNotThrowAnyException();
        assertThat(race.status(leader, crewId, Optional.empty()).total())
                .isEqualTo(Distance.ofMeters(3_000));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("주간 거리 합산이 계속 실패해도 수신자는 예외를 던지지 않는다(재합산이 다시 맞춘다)")
    void weeklyRace_persistentFailure_isContained() {
        long leader =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        "수신러너" + NAMES.incrementAndGet()))
                                        .getId());
        crewLedBy(leader);
        doThrow(new IllegalStateException("db down")).when(weeklyRaces).addAtomically(any(), any());

        assertThatCode(() -> race.onRunSaved(saved(leader, null, RunMode.FREE)))
                .doesNotThrowAnyException();
        verify(weeklyRaces, atLeast(1)).addAtomically(any(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루런 러닝 연결이 일시 오류를 받으면 다시 하고, 계속 실패해도 예외를 던지지 않는다")
    void crewRunLink_retriesAndContains() {
        doThrow(new CannotAcquireLockException("lock wait"))
                .doCallRealMethod()
                .when(crewPosts)
                .findCrewRunByIdForUpdate(anyLong());
        assertThatCode(() -> crewRuns.onRunSaved(saved(1L, 987_654L, RunMode.CREW_RUN)))
                .doesNotThrowAnyException();
        verify(crewPosts, times(2)).findCrewRunByIdForUpdate(987_654L);

        reset(crewPosts);
        doThrow(new IllegalStateException("db down"))
                .when(crewPosts)
                .findCrewRunByIdForUpdate(anyLong());
        assertThatCode(() -> crewRuns.onRunSaved(saved(1L, 987_654L, RunMode.CREW_RUN)))
                .doesNotThrowAnyException();
        verify(crewPosts, atLeast(1)).findCrewRunByIdForUpdate(987_654L);
        doCallRealMethod().when(crewPosts).findCrewRunByIdForUpdate(anyLong());
    }
}
