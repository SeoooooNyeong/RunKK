package com.a207.runkk.batch.running;

import com.a207.runkk.running.RunRejudgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동이 끝나면 v0.10 일회성 재판정(RunRejudgeService)을 한 번 부른다(사용자 결정, FR-047 변경). Flyway Java 마이그레이션 대신 기동
 * 작업으로 둔 까닭: 재판정에는 경로 압축 해제·최고 기록 장부·티어 반영·리워드 적립·배경 해금 같은 도메인 규칙과 사건 수신자가 필요하고, 마이그레이션 트랜잭션 하나로
 * 돌리면 배포가 길어지고 표를 오래 잠근다. V28(SQL)은 대상(배포 시점의 무효 기록)에 표시만 세우고, 이 작업이 기록마다 작은 트랜잭션으로 표시를 옮긴다. 남은 일이
 * 없으면 색인 조회 두 번으로 끝난다. 실패는 운영 로그로 남기고 기동은 막지 않는다(남은 기록은 다음 기동이 잇는다).
 * runkk.batch.run-rejudge.enabled=false로 끌 수 있다.
 *
 * <p>D-199(7차 리뷰 BE7-1): 기동 완료 사건을 받는 스레드에서 돌리면 그동안 readiness가 올라가지 않아 배포 상태 확인이 기다린다. 공용 비동기 실행기에서
 * 돌리고 한 번 실행은 Budget.PER_RUN(1,000건 또는 60초)까지만 한다. 같은 사건을 받는 대결 시계 복구와는 서로 기다리지 않는다.
 */
@Component
@ConditionalOnProperty(
        name = "runkk.batch.run-rejudge.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RunRejudgeJob {

    private static final Logger log = LoggerFactory.getLogger(RunRejudgeJob.class);

    private final RunRejudgeService runRejudgeService;

    public RunRejudgeJob(RunRejudgeService runRejudgeService) {
        this.runRejudgeService = runRejudgeService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            runRejudgeService.run();
        } catch (RuntimeException e) {
            log.error("run_rejudge_job_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
