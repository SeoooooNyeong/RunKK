package com.a207.runkk.batch.runbody;

import com.a207.runkk.runbody.RunBodyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 5초마다 런바디 작업을 진행시킨다(설계 05-19 D-443). RunBodyService.pollDue 하나만 부른다. */
@Component
public class RunBodyPollJob {

    private static final Logger log = LoggerFactory.getLogger(RunBodyPollJob.class);

    private final RunBodyService service;

    public RunBodyPollJob(RunBodyService service) {
        this.service = service;
    }

    public void run() {
        try {
            service.pollDue();
        } catch (RuntimeException error) {
            log.error("runbody_poll_batch_failed error={}", error.getClass().getSimpleName());
        }
    }
}
