package com.a207.runkk.audit;

import com.a207.runkk.common.DateRange;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 감사 기록 저장 포트(설계 6.1.11). 구현은 infra.audit.JpaAuditRepository. update 메소드를 두지 않는다. */
public interface AuditRepository {

    void append(AuditRecord record);

    /** 기간(시작일 0시 ~ 종료일 다음 날 0시 전) 안의 기록, 최신순. */
    List<AuditRecord> find(DateRange range, Optional<AuditAction> action);

    int deleteOlderThan(LocalDateTime cutoff);
}
