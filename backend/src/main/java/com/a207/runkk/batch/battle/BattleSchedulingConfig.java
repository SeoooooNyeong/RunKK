package com.a207.runkk.batch.battle;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * batch.battle 작업의 실행 간격(설계 6.1.13 …SchedulingConfig, D-39). 대결 시계 기본 1초(srs.md 6.3 4.2 "1초 간격"),
 * 출발하지 못한 대결 정리 기본 1분(활성 대결 1개 불변식 결정). 시험 프로필은 runkk.batch.battle-tick-enabled=false로 둘 다 끄고 연산을
 * 직접 부른다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "runkk.batch.battle-tick-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BattleSchedulingConfig implements SchedulingConfigurer {

    private final BattleTickJob battleTickJob;
    private final AbandonedBattleJob abandonedBattleJob;
    private final Duration interval;
    private final Duration abandonInterval;

    public BattleSchedulingConfig(
            BattleTickJob battleTickJob,
            AbandonedBattleJob abandonedBattleJob,
            @Value("${runkk.batch.battle-tick-interval:1s}") Duration interval,
            @Value("${runkk.batch.battle-abandon-interval:1m}") Duration abandonInterval) {
        this.battleTickJob = battleTickJob;
        this.abandonedBattleJob = abandonedBattleJob;
        this.interval = interval;
        this.abandonInterval = abandonInterval;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(battleTickJob::run, interval);
        registrar.addFixedDelayTask(abandonedBattleJob::run, abandonInterval);
    }
}
