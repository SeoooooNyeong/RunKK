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
import com.a207.runkk.batch.reward.RewardReconcileJob;
import com.a207.runkk.batch.reward.RewardSchedulingConfig;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewRunLinkReconcileService;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.reward.RewardReconcileService;
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
    @DisplayName(
            "M4 결정: CrewRunLinkReconcileJob → reconcileLinks(now), RewardReconcileJob →"
                    + " reconcile(now), 실패해도 던지지 않는다")
    void receiverReconcileJobs() {
        CrewRunLinkReconcileService links = mock(CrewRunLinkReconcileService.class);
        new CrewRunLinkReconcileJob(links, clock).run();
        verify(links).reconcileLinks(NOW);
        when(links.reconcileLinks(any())).thenThrow(new IllegalStateException("db"));
        new CrewRunLinkReconcileJob(links, clock).run();

        RewardReconcileService rewards = mock(RewardReconcileService.class);
        new RewardReconcileJob(rewards, clock).run();
        verify(rewards).reconcile(NOW);
        when(rewards.reconcile(any())).thenThrow(new IllegalStateException("db"));
        new RewardReconcileJob(rewards, clock).run();

        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new RewardSchedulingConfig(
                        mock(RewardReconcileJob.class),
                        new RewardSchedulingConfig.Schedule("0 10 0 * * *"))
                .configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
    }

    @Test
    @DisplayName("PollReminderJob(1분, D-106) → remindClosingPolls(now), 실패해도 던지지 않는다")
    void pollReminderJob() {
        CrewPostService posts = mock(CrewPostService.class);
        new PollReminderJob(posts, clock).run();
        verify(posts).remindClosingPolls(NOW);
        when(posts.remindClosingPolls(any())).thenThrow(new IllegalStateException("db"));
        new PollReminderJob(posts, clock).run();
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
        CrewRunLinkReconcileJob links = mock(CrewRunLinkReconcileJob.class);
        PollReminderJob polls = mock(PollReminderJob.class);

        ScheduledTaskRegistrar on = new ScheduledTaskRegistrar();
        new CrewSchedulingConfig(
                        tick,
                        reconcile,
                        expiry,
                        links,
                        polls,
                        new CrewSchedulingConfig.Schedule(
                                true,
                                Duration.ofSeconds(1),
                                "0 0 0 * * *",
                                "0 0 3 * * *",
                                "0 10 0 * * *",
                                Duration.ofMinutes(1)))
                .configureTasks(on);
        assertThat(on.getFixedDelayTaskList()).as("1초 크루런 시계 + 1분 투표 마감 알림").hasSize(2);
        assertThat(on.getFixedDelayTaskList())
                .extracting(t -> t.getIntervalDuration())
                .containsExactlyInAnyOrder(Duration.ofSeconds(1), Duration.ofMinutes(1));
        assertThat(on.getCronTaskList()).hasSize(3);

        ScheduledTaskRegistrar off = new ScheduledTaskRegistrar();
        new CrewSchedulingConfig(
                        tick,
                        reconcile,
                        expiry,
                        links,
                        polls,
                        new CrewSchedulingConfig.Schedule(
                                false,
                                Duration.ofSeconds(1),
                                "0 0 0 * * *",
                                "0 0 3 * * *",
                                "0 10 0 * * *",
                                Duration.ofMinutes(1)))
                .configureTasks(off);
        assertThat(off.getFixedDelayTaskList()).as("시계를 꺼도 투표 마감 알림은 돈다").hasSize(1);

        ScheduledTaskRegistrar avatar = new ScheduledTaskRegistrar();
        new AvatarSchedulingConfig(
                        mock(AppearanceRefreshJob.class),
                        mock(com.a207.runkk.batch.avatar.BackgroundReconcileJob.class),
                        new AvatarSchedulingConfig.Schedule("0 0 0 * * *", "0 10 0 * * *"))
                .configureTasks(avatar);
        assertThat(avatar.getCronTaskList()).hasSize(2);
    }
}
