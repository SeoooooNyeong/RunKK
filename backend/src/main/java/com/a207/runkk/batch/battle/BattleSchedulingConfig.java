package com.a207.runkk.batch.battle;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * batch.battle 작업의 실행 간격(설계 6.1.13 …SchedulingConfig, D-39). 기본 1초(srs.md 6.3 4.2 "1초 간격"). 시험 프로필은
 * runkk.batch.battle-tick-enabled=false로 끄고 시계 연산을 직접 부른다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "runkk.batch.battle-tick-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BattleSchedulingConfig implements SchedulingConfigurer {

    private final BattleTickJob battleTickJob;
    private final Duration interval;

    public BattleSchedulingConfig(
            BattleTickJob battleTickJob,
            @Value("${runkk.batch.battle-tick-interval:1s}") Duration interval) {
        this.battleTickJob = battleTickJob;
        this.interval = interval;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(battleTickJob::run, interval);
    }
}
