package com.a207.runkk.crew;

import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.SavedRun;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 크루런 기록 연결 일 단위 보정(코드 리뷰 2차 M4 결정, D-38 주간 거리 재합산과 같은 방식). 커밋 뒤 수신자(CrewRunService.onRunSaved)가
 * 재시도 3회까지 실패해 빠진 연결을, batch.crew의 CrewRunLinkReconcileJob이 매일 0시 10분에 now만 넘겨 채운다. 최근 {@value
 * #LOOKBACK_DAYS}일 0시부터 now까지 저장된 크루런 기록마다 CrewRunService.relink를 부른다. 참여에 연결이 이미 있으면 건너뛰고 한 참여에 러닝
 * 1건(FR-146)이라 여러 번 돌려도 같다(멱등). 크루런이 이미 끝났으면(FINISHED) 연결만 하고 도착·순위는 바꾸지 않는다(수신자의 늦은 업로드와 같다).
 */
@Service
public class CrewRunLinkReconcileService {

    /** 오늘 0시에서 거슬러 볼 날 수(reward 보정과 같다). */
    static final int LOOKBACK_DAYS = 2;

    private static final Logger log = LoggerFactory.getLogger(CrewRunLinkReconcileService.class);

    private final CrewRunService crewRunService;
    private final RunQueryService runQueryService;

    CrewRunLinkReconcileService(CrewRunService crewRunService, RunQueryService runQueryService) {
        this.crewRunService = crewRunService;
        this.runQueryService = runQueryService;
    }

    /** 빠진 연결을 채우고 새로 이은 건수를 돌려준다. */
    public int reconcileLinks(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().minusDays(LOOKBACK_DAYS).atStartOfDay();
        int linked = 0;
        for (SavedRun run : runQueryService.savedBetween(from, now)) {
            if (run.crewRunPostId() != null) {
                linked += crewRunService.relink(run);
            }
        }
        log.info("crew_run_links_reconciled from={} linked={}", from, linked);
        return linked;
    }
}
