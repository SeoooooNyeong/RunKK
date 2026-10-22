package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.CrewRunService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 1초마다 크루런 시계 연산을 부른다(설계 6.1.13 batch.crew, 5.5 시각 작업, D-39): CrewRunService.tick,
 * CrewRunLiveService.detectTimeouts. 같은 now를 넘기고 한 호출의 실패가 다른 호출을 막지 않게 따로 감싼다. 업무 규칙이 없다.
 */
@Component
public class CrewRunTickJob {

    private static final Logger log = LoggerFactory.getLogger(CrewRunTickJob.class);

    private final CrewRunService crewRunService;
    private final CrewRunLiveService crewRunLiveService;
    private final Clock clock;

    public CrewRunTickJob(
            CrewRunService crewRunService, CrewRunLiveService crewRunLiveService, Clock clock) {
        this.crewRunService = crewRunService;
        this.crewRunLiveService = crewRunLiveService;
        this.clock = clock;
    }

    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        guarded("crew_run_tick", crewRunService::tick, now);
        guarded("crew_run_timeouts", crewRunLiveService::detectTimeouts, now);
    }

    private static void guarded(String job, Consumer<LocalDateTime> call, LocalDateTime now) {
        try {
            call.accept(now);
        } catch (RuntimeException e) {
            log.error("{}_failed error={}", job, e.getClass().getSimpleName(), e);
        }
    }
}
