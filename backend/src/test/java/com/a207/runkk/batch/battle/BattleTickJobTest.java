package com.a207.runkk.batch.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.a207.runkk.battle.BattleClockService;
import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.battle.Matchmaker;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** batch.battle(설계 6.1.13, 5.5): 1초 작업이 같은 now로 세 연산을 부르고 한 호출의 실패가 다른 호출을 막지 않는다. */
@DisplayName("batch.battle 1초 대결 시계")
class BattleTickJobTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0, 1);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Test
    @DisplayName(
            "Matchmaker.tick → BattleClockService.tick → LiveBattleService.detectTimeouts(now), 앞"
                    + " 호출이 실패해도 이어 간다")
    void run_callsAllThree_isolatingFailures() {
        Matchmaker matchmaker = mock(Matchmaker.class);
        BattleClockService clockService = mock(BattleClockService.class);
        LiveBattleService live = mock(LiveBattleService.class);
        doThrow(new IllegalStateException("redis")).when(matchmaker).tick(any());
        doThrow(new IllegalStateException("db")).when(clockService).tick(any());

        new BattleTickJob(matchmaker, clockService, live, clock).run();

        verify(matchmaker).tick(NOW);
        verify(clockService).tick(NOW);
        verify(live).detectTimeouts(NOW);
    }

    @Test
    @DisplayName("스케줄 설정은 1초 시계, 1분 버려진 대결 정리, 5초 친구 대결 초대 만료(D-221) 고정 간격 작업 셋을 등록한다")
    void schedulingConfig_registersFixedDelay() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new BattleSchedulingConfig(
                        mock(BattleTickJob.class),
                        mock(AbandonedBattleJob.class),
                        mock(FriendInviteExpiryJob.class),
                        mock(PlacementReconcileJob.class),
                        new BattleSchedulingConfig.Schedule(
                                Duration.ofSeconds(1),
                                Duration.ofMinutes(1),
                                Duration.ofSeconds(5),
                                "0 20 0 * * *"))
                .configureTasks(registrar);
        assertThat(registrar.getFixedDelayTaskList()).hasSize(3);
        assertThat(registrar.getCronTaskList())
                .singleElement()
                .satisfies(t -> assertThat(t.getExpression()).isEqualTo("0 20 0 * * *"));
        assertThat(registrar.getFixedDelayTaskList().get(2).getIntervalDuration())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("AbandonedBattleJob → cancelAbandoned(now), 실패해도 던지지 않는다")
    void abandonedBattleJob() {
        BattleStartService starts = mock(BattleStartService.class);
        new AbandonedBattleJob(starts, clock).run();
        verify(starts).cancelAbandoned(NOW);
        org.mockito.Mockito.when(starts.cancelAbandoned(any()))
                .thenThrow(new IllegalStateException("db"));
        new AbandonedBattleJob(starts, clock).run();
    }
}
