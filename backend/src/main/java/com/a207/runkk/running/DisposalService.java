package com.a207.runkk.running;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.ConsentWithdrawn;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 원자료 파기 유스케이스를 조정한다(설계 6.1.3, SQ-13, FR-026, FR-029). 위치·심박 동의 철회 사건을 받아 7일 기한의 지시를 만들고, 매일 3시에 서버
 * 기록의 경로·심박을 지운 뒤 감사(위치경로파기)를 남긴다. 기기 쪽 파기는 앱이 pendingFor·confirmDevice로 맞춘다.
 */
@Service
public class DisposalService {

    private static final Logger log = LoggerFactory.getLogger(DisposalService.class);
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final DisposalOrderRepository disposalOrderRepository;
    private final RunRecordRepository runRecordRepository;
    private final AuditService auditService;
    private final Clock clock;

    public DisposalService(
            DisposalOrderRepository disposalOrderRepository,
            RunRecordRepository runRecordRepository,
            AuditService auditService,
            Clock clock) {
        this.disposalOrderRepository = disposalOrderRepository;
        this.runRecordRepository = runRecordRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** 철회 + 7일 기한의 DisposalOrder(ROUTE 또는 HEART_RATE). 철회 커밋 뒤 새 트랜잭션에서 돈다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConsentWithdrawn(ConsentWithdrawn e) {
        DisposalKind kind;
        if (e.item() == ConsentItem.LOCATION) {
            kind = DisposalKind.ROUTE;
        } else if (e.item() == ConsentItem.HEART_RATE) {
            kind = DisposalKind.HEART_RATE;
        } else {
            return;
        }
        disposalOrderRepository.save(DisposalOrder.schedule(e.memberId(), kind, e.at()));
    }

    /**
     * SCHEDULED 지시마다 그 회원 기록 전부 purgeRoute / purgeHeartRates → execute → 감사(ROUTE_DISPOSAL, 심박이면
     * before "HEART_RATE"). batch.running의 DisposalJob이 3시에 부른다. 처리 수 반환.
     */
    @Transactional
    public int executeDue(LocalDateTime now) {
        int executed = 0;
        for (DisposalOrder order : disposalOrderRepository.findScheduled()) {
            if (!order.isDue(now)) {
                continue;
            }
            if (order.isOverdue(now)) {
                log.warn("disposal_overdue orderId={} kind={}", order.id(), order.kind());
            }
            List<RunRecord> records = runRecordRepository.findByMember(order.memberId());
            for (RunRecord record : records) {
                if (order.kind() == DisposalKind.ROUTE) {
                    record.purgeRoute();
                } else {
                    record.purgeHeartRates();
                }
                runRecordRepository.save(record);
            }
            order.execute(now);
            disposalOrderRepository.save(order);
            String memberId = String.valueOf(order.memberId());
            auditService.record(
                    new AuditEntry(
                            SYSTEM_ACTOR,
                            ActorRole.SYSTEM,
                            AuditAction.ROUTE_DISPOSAL,
                            memberId,
                            order.kind() == DisposalKind.HEART_RATE ? "HEART_RATE" : "ROUTE",
                            "records=" + records.size()));
            executed++;
        }
        log.info("disposal_executed count={}", executed);
        return executed;
    }

    /** 기기가 아직 확인하지 않은 지시(GET /me/disposals). */
    @Transactional(readOnly = true)
    public List<DisposalOrderView> pendingFor(long memberId) {
        return disposalOrderRepository.findNotConfirmedByMember(memberId).stream()
                .map(DisposalOrder::view)
                .toList();
    }

    /** 기기 파기 확인. 본인 지시만. */
    @Transactional
    public void confirmDevice(long memberId, long orderId) {
        DisposalOrder order =
                disposalOrderRepository
                        .findById(orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                ReasonCode.DISPOSAL_ORDER_NOT_FOUND));
        if (order.memberId() != memberId) {
            throw new PermissionDeniedException("남의 파기 지시");
        }
        order.confirmDevice(LocalDateTime.now(clock));
        disposalOrderRepository.save(order);
    }
}
