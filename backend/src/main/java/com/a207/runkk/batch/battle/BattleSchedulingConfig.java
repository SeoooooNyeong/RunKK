package com.a207.runkk.batch.battle;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * batch.battle 작업의 실행 간격(설계 6.1.13 …SchedulingConfig, D-39). 대결 시계 기본 1초(srs.md 6.3 4.2 "1초 간격"),
 * 출발하지 못한 대결 정리 기본 1분(활성 대결 1개 불변식 결정), 친구 대결 초대 만료 정리 기본 5초(v0.14 D-221). 시험 프로필은
 * runkk.batch.battle-tick-enabled=false로 둘 다 끄고 연산을 직접 부른다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BattleSchedulingConfig.Schedule.class)
@ConditionalOnProperty(
        name = "runkk.batch.battle-tick-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BattleSchedulingConfig implements SchedulingConfigurer {

    private final BattleTickJob battleTickJob;
    private final AbandonedBattleJob abandonedBattleJob;
    private final FriendInviteExpiryJob friendInviteExpiryJob;
    private final Duration interval;
    private final Duration abandonInterval;
    private final Duration friendInviteExpiryInterval;

    public BattleSchedulingConfig(
            BattleTickJob battleTickJob,
            AbandonedBattleJob abandonedBattleJob,
            FriendInviteExpiryJob friendInviteExpiryJob,
            Schedule schedule) {
        this.battleTickJob = battleTickJob;
        this.abandonedBattleJob = abandonedBattleJob;
        this.friendInviteExpiryJob = friendInviteExpiryJob;
        this.interval = schedule.battleTickInterval();
        this.abandonInterval = schedule.battleAbandonInterval();
        this.friendInviteExpiryInterval = schedule.friendInviteExpiryInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(battleTickJob::run, interval);
        registrar.addFixedDelayTask(abandonedBattleJob::run, abandonInterval);
        registrar.addFixedDelayTask(friendInviteExpiryJob::run, friendInviteExpiryInterval);
    }

    /**
     * 이 패키지 작업의 실행 시각·간격(runkk.batch.*, 코드 리뷰 2차 L1: 값 주입 대신 기능별 설정 record).
     *
     * @param battleTickInterval runkk.batch.battle-tick-interval
     * @param battleAbandonInterval runkk.batch.battle-abandon-interval
     * @param friendInviteExpiryInterval runkk.batch.friend-invite-expiry-interval(친구 대결 초대 만료 정리,
     *     D-221)
     */
    @ConfigurationProperties("runkk.batch")
    public record Schedule(
            @DefaultValue("1s") Duration battleTickInterval,
            @DefaultValue("1m") Duration battleAbandonInterval,
            @DefaultValue("5s") Duration friendInviteExpiryInterval) {}
}
