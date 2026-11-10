package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.CrewRunRepeater;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 매주 일요일 0시 5분(한국 시간) 크루런 매주 반복의 다음 주 회차를 만든다(W6, D-성-26). now만 넘기고 대상 반복·멱등은
 * CrewRunRepeater.generateDue가 맡는다. 실패는 운영 로그로 남긴다.
 */
@Component
public class CrewRunRepeatJob {

    private static final Logger log = LoggerFactory.getLogger(CrewRunRepeatJob.class);

    private final CrewRunRepeater crewRunRepeater;
    private final Clock clock;

    public CrewRunRepeatJob(CrewRunRepeater crewRunRepeater, Clock clock) {
        this.crewRunRepeater = crewRunRepeater;
        this.clock = clock;
    }

    public void run() {
        try {
            int created = crewRunRepeater.generateDue(LocalDateTime.now(clock));
            log.info("crew_run_repeat created={}", created);
        } catch (RuntimeException e) {
            log.error("crew_run_repeat_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
