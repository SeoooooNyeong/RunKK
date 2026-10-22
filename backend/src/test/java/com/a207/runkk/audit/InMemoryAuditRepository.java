package com.a207.runkk.audit;

import com.a207.runkk.common.DateRange;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 시험용 인메모리 AuditRepository(설계 13.1 "테스트는 InMemory…Repository"). 추가 전용이다. */
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public void append(AuditRecord record) {
        records.add(record);
    }

    @Override
    public List<AuditRecord> find(DateRange range, Optional<AuditAction> action) {
        LocalDateTime from = range.start().atStartOfDay();
        LocalDateTime to = range.end().plusDays(1).atStartOfDay();
        return records.stream()
                .filter(r -> !r.view().at().isBefore(from) && r.view().at().isBefore(to))
                .filter(r -> action.map(a -> a == r.view().action()).orElse(true))
                .sorted(Comparator.comparing((AuditRecord r) -> r.view().at()).reversed())
                .toList();
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff) {
        int before = records.size();
        records.removeIf(r -> r.view().at().isBefore(cutoff));
        return before - records.size();
    }

    public int size() {
        return records.size();
    }

    public List<AuditRecordView> all() {
        return records.stream().map(AuditRecord::view).toList();
    }
}
