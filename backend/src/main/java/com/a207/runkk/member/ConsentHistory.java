package com.a207.runkk.member;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 동의 항목별 변경 이력을 덧붙여 보관한다(설계 6.1.2, FR-017). 이전 이력을 지우지 않는다. */
@Embeddable
public class ConsentHistory {

    @ElementCollection
    @CollectionTable(name = "member_consent", joinColumns = @JoinColumn(name = "member_id"))
    @OrderColumn(name = "seq")
    private List<ConsentRecord> records = new ArrayList<>();

    public ConsentHistory() {}

    /** 이력 덧붙이기. */
    public void record(ConsentItem item, boolean agreed, LocalDateTime at) {
        records.add(new ConsentRecord(item, agreed, at));
    }

    void record(ConsentItem item, LocalDateTime at, int noticeVersion) {
        records.add(new ConsentRecord(item, true, at, noticeVersion));
    }

    /** 가장 최근 값. 이력이 없으면 false. */
    public boolean isAgreed(ConsentItem item) {
        return agreedVersion(item) > 0;
    }

    int agreedVersion(ConsentItem item) {
        for (int i = records.size() - 1; i >= 0; i--) {
            ConsentRecord record = records.get(i);
            if (record.item() == item) {
                return record.agreed() ? record.noticeVersion() : 0;
            }
        }
        return 0;
    }

    /** 탈퇴 때 비운다. 감사 로그의 동의 기록은 AuditRecord에 따로 남아 6개월 보존된다. */
    public void wipe() {
        records.clear();
    }

    public List<ConsentRecord> records() {
        return Collections.unmodifiableList(records);
    }
}
