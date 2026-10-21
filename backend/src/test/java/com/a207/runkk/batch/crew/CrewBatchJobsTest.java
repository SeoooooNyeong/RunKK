package com.a207.runkk.batch.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.avatar.AppearanceService;
import com.a207.runkk.batch.avatar.AppearanceRefreshJob;
import com.a207.runkk.batch.avatar.AvatarSchedulingConfig;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.WeeklyRaceService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** batch.crew·batch.avatar 작업(설계 6.1.13, D-39): 고정 시계의 now로 응용 서비스 연산만 부르고 실패를 삼켜 로그로 남긴다. */
@DisplayName("시각 작업(B4 범위: batch.crew·batch.avatar)")
class CrewBatchJobsTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Test
    @DisplayName("CrewRunTickJob → tick(now)·detectTimeouts(now), 한쪽 실패가 다른 쪽을 막지 않는다")
    void crewRunTickJob() {
        CrewRunService runs = mock(CrewRunService.class);
        CrewRunLiveService live = mock(CrewRunLiveService.class);
        doThrow(new IllegalStateException("db")).when(runs).tick(any());

        new CrewRunTickJob(runs, live, clock).run();

        verify(runs).tick(NOW);
        verify(live).detectTimeouts(NOW);
    }

    @Test
    @DisplayName(
            "WeeklyRaceReconcileJob → reconcileAll(now), JoinRequestExpiryJob →"
                    + " expireRequests(now)")
    void dailyJobs() {
        WeeklyRaceService race = mock(WeeklyRaceService.class);
        new WeeklyRaceReconcileJob(race, clock).run();
        verify(race).reconcileAll(NOW);
        when(race.reconcileAll(any())).thenThrow(new IllegalStateException("db"));
        new WeeklyRaceReconcileJob(race, clock).run();

        MembershipService membership = mock(MembershipService.class);
        new JoinRequestExpiryJob(membership, clock).run();
        verify(membership).expireRequests(NOW);
        when(membership.expireRequests(any())).thenThrow(new IllegalStateException("db"));
        new JoinRequestExpiryJob(membership, clock).run();
    }

    @Test
    @DisplayName("AppearanceRefreshJob → refreshAll(today)")
    void appearanceRefreshJob() {
        AppearanceService appearance = mock(AppearanceService.class);
        new AppearanceRefreshJob(appearance, clock).run();
        verify(appearance).refreshAll(NOW.toLocalDate());
        when(appearance.refreshAll(any())).thenThrow(new IllegalStateException("db"));
        new AppearanceRefreshJob(appearance, clock).run();
    }

    @Test
    @DisplayName("스케줄 설정: 크루런 시계는 켰을 때만, 0시·3시 작업은 항상 등록")
    void schedulingConfigs() {
        CrewRunTickJob tick = mock(CrewRunTickJob.class);
        WeeklyRaceReconcileJob reconcile = mock(WeeklyRaceReconcileJob.class);
        JoinRequestExpiryJob expiry = mock(JoinRequestExpiryJob.class);

        ScheduledTaskRegistrar on = new ScheduledTaskRegistrar();
        new CrewSchedulingConfig(
                        tick,
                        reconcile,
                        expiry,
                        true,
                        Duration.ofSeconds(1),
                        "0 0 0 * * *",
                        "0 0 3 * * *")
                .configureTasks(on);
        assertThat(on.getFixedDelayTaskList()).hasSize(1);
        assertThat(on.getCronTaskList()).hasSize(2);

        ScheduledTaskRegistrar off = new ScheduledTaskRegistrar();
        new CrewSchedulingConfig(
                        tick,
                        reconcile,
                        expiry,
                        false,
                        Duration.ofSeconds(1),
                        "0 0 0 * * *",
                        "0 0 3 * * *")
                .configureTasks(off);
        assertThat(off.getFixedDelayTaskList()).isEmpty();

        ScheduledTaskRegistrar avatar = new ScheduledTaskRegistrar();
        new AvatarSchedulingConfig(mock(AppearanceRefreshJob.class), "0 0 0 * * *")
                .configureTasks(avatar);
        assertThat(avatar.getCronTaskList()).hasSize(1);
    }
}
