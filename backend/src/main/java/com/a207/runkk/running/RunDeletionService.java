package com.a207.runkk.running;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집계 제외 기록을 지우고 다시 올라오지 않게 묘비를 남긴다(설계 5.6.2, SQ-33, FR-225, D-231·D-232). 유효·집계 제외 판정은 서버만 하고, 집계
 * 제외 기록은 랭킹·티어·리워드·달력에 들어가 있지 않아 다시 계산할 것이 없다.
 */
@Service
public class RunDeletionService {

    private static final Logger log = LoggerFactory.getLogger(RunDeletionService.class);

    private final RunRecordRepository runRecordRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RunDeletionService(
            RunRecordRepository runRecordRepository,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.runRecordRepository = runRecordRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 행 잠금으로 읽어 검사한다: 없으면 RECORD_NOT_FOUND, 주인이 아니면 PermissionDeniedException(주인 검사가 먼저), 삭제할 수 없으면
     * RECORD_VALID_NOT_DELETABLE. 지우고 묘비를 남긴 뒤 RunRecordDeleted를 발행한다(수신자는 커밋 뒤). 되돌릴 수 없다.
     */
    @Transactional
    public void delete(long memberId, String recordId) {
        RunRecord record =
                runRecordRepository
                        .findForUpdate(recordId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
        if (record.getMemberId() != memberId) {
            throw new PermissionDeniedException("본인 기록만 삭제할 수 있다");
        }
        if (!record.isDeletable()) {
            throw new BusinessRuleException(ReasonCode.RECORD_VALID_NOT_DELETABLE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        runRecordRepository.delete(record);
        runRecordRepository.bury(recordId, memberId, now);
        eventPublisher.publish(
                new RunRecordDeleted(
                        recordId, memberId, record.battleId(), record.crewRunPostId(), now));
        log.info("run_record_deleted recordId={} memberId={}", recordId, memberId);
    }
}
