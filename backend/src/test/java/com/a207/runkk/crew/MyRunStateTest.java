package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 열람자의 크루런 참여 상태(v0.14 S15P31A207-314): 참여 없음·진행 참가자 아님 NONE, 측정 중 RUNNING, 측정 끝 ENDED. */
@DisplayName("MyRunState.of: 참여 → 내 크루런 상태")
class MyRunStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 7, 0);

    @Test
    void noParticipationIsNone() {
        assertThat(MyRunState.of(Optional.empty())).isEqualTo(MyRunState.NONE);
    }

    @Test
    void votedOrReadyButNotRunnerIsNone() {
        CrewRunParticipation going = new CrewRunParticipation(1L, AttendanceAnswer.GOING, NOW);
        assertThat(MyRunState.of(Optional.of(going))).isEqualTo(MyRunState.NONE);
        going.enter();
        going.changeReady(true);
        assertThat(MyRunState.of(Optional.of(going))).isEqualTo(MyRunState.NONE);
    }

    @Test
    void runnerIsRunningUntilEnded() {
        CrewRunParticipation p = new CrewRunParticipation(1L, AttendanceAnswer.GOING, NOW);
        p.markRunner(NOW, "11111111-1111-1111-1111-111111111111");
        assertThat(MyRunState.of(Optional.of(p))).isEqualTo(MyRunState.RUNNING);
        p.end(null, null);
        assertThat(MyRunState.of(Optional.of(p))).isEqualTo(MyRunState.ENDED);
    }
}
