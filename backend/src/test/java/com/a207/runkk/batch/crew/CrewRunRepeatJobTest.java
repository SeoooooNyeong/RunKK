package com.a207.runkk.batch.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.crew.CrewRunRepeater;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** 반복 크루런 회차 작업(W6, S15P31A207-297): 고정 시계의 now로 generateDue만 부르고 실패를 삼킨다(D-성-26). */
@DisplayName("반복 크루런 회차 작업(W6)")
class CrewRunRepeatJobTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 7, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Test
    @DisplayName("run → generateDue(now), 실패해도 던지지 않는다")
    void run() {
        CrewRunRepeater repeater = mock(CrewRunRepeater.class);
        new CrewRunRepeatJob(repeater, clock).run();
        verify(repeater).generateDue(NOW);

        when(repeater.generateDue(any())).thenThrow(new IllegalStateException("db"));
        new CrewRunRepeatJob(repeater, clock).run();
    }

    @Test
    @DisplayName("D-성-26: 매주 일요일 0시 5분(Asia/Seoul) cron 작업 하나를 건다")
    void schedule() {
        CrewRunRepeatJob job = new CrewRunRepeatJob(mock(CrewRunRepeater.class), clock);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new CrewRunRepeatSchedulingConfig(
                        job, new CrewRunRepeatSchedulingConfig.Schedule("0 5 0 * * SUN"))
                .configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        assertThat(registrar.getCronTaskList().get(0).getExpression()).isEqualTo("0 5 0 * * SUN");
    }
}
