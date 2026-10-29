package com.a207.runkk.running;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 경로 모양이 없는 기존 기록에 목록 썸네일 모양을 채운다(설계 5.6.2, D-234). batch.running.RouteShapeBackfillJob이 기동 뒤 남은 것이
 * 없을 때까지 부른다. 기록마다 새 트랜잭션이라 한 건의 실패가 다른 건을 막지 않는다.
 */
@Service
public class RouteShapeBackfillService {

    private static final Logger log = LoggerFactory.getLogger(RouteShapeBackfillService.class);

    private final RunRecordRepository runRecordRepository;
    private final TransactionTemplate transaction;

    public RouteShapeBackfillService(
            RunRecordRepository runRecordRepository,
            PlatformTransactionManager transactionManager) {
        this.runRecordRepository = runRecordRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 모양이 없고 경로가 남은 기록을 limit개까지 채운다. 채운 수(0이면 남은 것이 없다). */
    public int fillMissing(int limit) {
        int filled = 0;
        for (RunRecord candidate : runRecordRepository.findMissingShape(limit)) {
            String recordId = candidate.getRecordId();
            try {
                Boolean done = transaction.execute(status -> fillOne(recordId));
                if (Boolean.TRUE.equals(done)) {
                    filled++;
                }
            } catch (RuntimeException e) {
                log.warn(
                        "route_shape_backfill_failed recordId={} error={}",
                        recordId,
                        e.getClass().getSimpleName());
            }
        }
        return filled;
    }

    private boolean fillOne(String recordId) {
        RunRecord record = runRecordRepository.findById(recordId).orElse(null);
        if (record == null || !record.hasRoute() || record.getRouteShape().isPresent()) {
            return false;
        }
        RouteShape shape = RouteShape.of(record.routePoints());
        if (shape.isEmpty()) {
            return false;
        }
        record.attachRouteShape(shape);
        runRecordRepository.save(record);
        return true;
    }
}
