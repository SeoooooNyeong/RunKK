package com.a207.runkk.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

/**
 * 동의 변경 한 건(자료사전 동의이력 = {동의항목 + 동의여부 + 변경일시}).
 *
 * @param item 동의 항목
 * @param agreed 동의 여부
 * @param changedAt 변경 일시
 */
@Embeddable
public record ConsentRecord(
        @Enumerated(EnumType.STRING) @Column(name = "item", nullable = false, length = 20)
                ConsentItem item,
        @Column(name = "agreed", nullable = false) boolean agreed,
        @Column(name = "changed_at", nullable = false) LocalDateTime changedAt,
        @Column(name = "notice_version", nullable = false) int noticeVersion) {

    public ConsentRecord(ConsentItem item, boolean agreed, LocalDateTime changedAt) {
        this(item, agreed, changedAt, 1);
    }
}
