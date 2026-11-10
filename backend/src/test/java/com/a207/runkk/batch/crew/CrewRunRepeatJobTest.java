package com.a207.runkk.batch.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.crew.CrewRunRepeater;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;

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
    @DisplayName("D-성-26(코드 리뷰 M6): 매주 일요일 오전 9시(Asia/Seoul) cron 작업 하나를 건다")
    void schedule() {
        CrewRunRepeatJob job = new CrewRunRepeatJob(mock(CrewRunRepeater.class), clock);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new CrewRunRepeatSchedulingConfig(
                        job, new CrewRunRepeatSchedulingConfig.Schedule("0 0 9 * * SUN"))
                .configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        assertThat(registrar.getCronTaskList().get(0).getExpression()).isEqualTo("0 0 9 * * SUN");
    }

    @Test
    @DisplayName("M6: 설정이 없을 때의 기본값과 application.yml 값이 모두 일요일 9시이고, 다음 실행은 일요일 09:00이다")
    void defaultIsSundayNine() throws IOException {
        CrewRunRepeatSchedulingConfig.Schedule defaults =
                new Binder(new MapConfigurationPropertySource(Map.of()))
                        .bindOrCreate("runkk.batch", CrewRunRepeatSchedulingConfig.Schedule.class);
        assertThat(defaults.crewRunRepeatCron()).isEqualTo("0 0 9 * * SUN");

        PropertySource<?> yml =
                new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))
                        .get(0);
        assertThat(yml.getProperty("runkk.batch.crew-run-repeat-cron")).isEqualTo("0 0 9 * * SUN");

        assertThat(CronExpression.parse("0 0 9 * * SUN").next(NOW))
                .as("토 07:00 다음 실행")
                .isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 0));
    }
}
