package com.a207.runkk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.batch.member.MemberSchedulingConfig;
import com.a207.runkk.batch.member.WithdrawalPurgeJob;
import com.a207.runkk.batch.notification.NotificationPurgeJob;
import com.a207.runkk.batch.notification.NotificationSchedulingConfig;
import com.a207.runkk.batch.running.DisposalJob;
import com.a207.runkk.batch.running.RunningSchedulingConfig;
import com.a207.runkk.member.WithdrawalService;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.running.DisposalService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * batch.member·running·notification 작업(설계 6.1.13, D-39): 고정 시계의 now로 서비스 연산 하나만 부르고 실패를 삼켜 로그로 남긴다.
 */
@DisplayName("시각 작업(B2 범위)")
class BatchJobsTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 3, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Test
    @DisplayName("WithdrawalPurgeJob → purgeWithdrawn(now), 실패해도 던지지 않는다")
    void withdrawalPurgeJob() {
        WithdrawalService service = mock(WithdrawalService.class);
        new WithdrawalPurgeJob(service, clock).run();
        verify(service).purgeWithdrawn(NOW);

        when(service.purgeWithdrawn(any())).thenThrow(new IllegalStateException("db"));
        new WithdrawalPurgeJob(service, clock).run();
    }

    @Test
    @DisplayName("DisposalJob → executeDue(now), NotificationPurgeJob → purgeExpired(now)")
    void disposalAndNotificationJobs() {
        DisposalService disposal = mock(DisposalService.class);
        new DisposalJob(disposal, clock).run();
        verify(disposal).executeDue(NOW);
        when(disposal.executeDue(any())).thenThrow(new IllegalStateException("db"));
        new DisposalJob(disposal, clock).run();

        NotificationService notifications = mock(NotificationService.class);
        new NotificationPurgeJob(notifications, clock).run();
        verify(notifications).purgeExpired(NOW);
        when(notifications.purgeExpired(any())).thenThrow(new IllegalStateException("db"));
        new NotificationPurgeJob(notifications, clock).run();
    }

    @Test
    @DisplayName("스케줄 설정은 cron 작업 하나씩을 등록한다")
    void schedulingConfigsRegisterCronTasks() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new MemberSchedulingConfig(mock(WithdrawalPurgeJob.class), "0 0 3 * * *")
                .configureTasks(registrar);
        new RunningSchedulingConfig(mock(DisposalJob.class), "0 0 3 * * *")
                .configureTasks(registrar);
        new NotificationSchedulingConfig(mock(NotificationPurgeJob.class), "0 0 3 * * *")
                .configureTasks(registrar);
        Assertions.assertThat(registrar.getCronTaskList()).hasSize(3);
    }
}
