package com.a207.runkk.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * 기준값 변경 한 건(설계 1.2.9, 자료사전 운영 기준 파일의 {기준항목 + 변경전값 + 변경후값 + 변경일시}). OperationSetting.change만 만들고
 * 고치지 않는다.
 */
@Entity
@Immutable
@Table(name = "operation_setting_history")
public class OperationSettingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting_key", nullable = false, updatable = false, length = 40)
    private SettingKey key;

    @Column(name = "before_value", nullable = false, updatable = false, columnDefinition = "text")
    private String before;

    @Column(name = "after_value", nullable = false, updatable = false, columnDefinition = "text")
    private String after;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "operator_id", nullable = false, updatable = false, length = 100)
    private String operatorId;

    protected OperationSettingHistory() {}

    OperationSettingHistory(
            SettingKey key,
            String before,
            String after,
            LocalDateTime changedAt,
            String operatorId) {
        this.key = key;
        this.before = before;
        this.after = after;
        this.changedAt = changedAt;
        this.operatorId = operatorId;
    }

    SettingKey key() {
        return key;
    }

    String before() {
        return before;
    }

    String after() {
        return after;
    }

    LocalDateTime changedAt() {
        return changedAt;
    }

    String operatorId() {
        return operatorId;
    }
}
