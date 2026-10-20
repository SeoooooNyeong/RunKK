package com.a207.runkk.audit;

import com.a207.runkk.common.DateRange;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 감사 기록 저장·조회·정리 유스케이스를 조정한다(설계 6.1.11). */
@Service
public class AuditService {

    /** 감사 기록 보관 기간(FR-176, NFR-035). */
    private static final int RETENTION_MONTHS = 6;

    private final AuditRepository auditRepository;
    private final Clock clock;

    public AuditService(AuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    /** 추가만. 호출한 트랜잭션 안에서 함께 커밋한다(감사 누락 방지). */
    @Transactional
    public void record(AuditEntry e) {
        auditRepository.append(AuditRecord.of(e, LocalDateTime.now(clock)));
    }

    /** 최신순 읽기 전용 투영(FR-177). */
    @Transactional(readOnly = true)
    public List<AuditRecordView> query(DateRange range, Optional<AuditAction> action) {
        return auditRepository.find(range, action).stream().map(AuditRecord::view).toList();
    }

    /** 기록일 + 6개월이 지난 것 삭제(FR-176). batch.audit의 AuditPurgeJob이 3시에 부른다. */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        return auditRepository.deleteOlderThan(now.minusMonths(RETENTION_MONTHS));
    }
}
