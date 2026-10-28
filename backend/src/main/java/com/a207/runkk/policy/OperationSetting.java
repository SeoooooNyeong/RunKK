package com.a207.runkk.policy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/** 운영 기준값 한 항목을 보관한다(설계 6.1.9). 변경은 version 낙관적 잠금(5.3). */
@Entity
@Table(name = "operation_setting")
public class OperationSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_key", length = 40)
    private SettingKey key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    /** 새 행이면 null(Hibernate가 저장 때 0으로 채운다). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OperationSetting() {}

    private OperationSetting(SettingKey key, String value, LocalDateTime now) {
        this.key = key;
        this.value = value;
        this.updatedAt = now;
    }

    /** 아직 저장된 적 없는 항목. 값은 srs.md 예시값(SettingKey.defaultValue)이다. */
    public static OperationSetting initial(SettingKey key, LocalDateTime now) {
        return new OperationSetting(key, key.defaultValue(), now);
    }

    /**
     * 값 검사와 바꾸기 전후 검사를 통과하면 값을 바꾸고 이전 값·새 값을 담은 이력을 돌려준다. 실패하면
     * BusinessRuleException(VALUE_INVALID)(FR-174). actorId는 확인된 운영자 계정 문자열이다(policy가 operator를
     * import하지 않는다).
     */
    public OperationSettingHistory change(String newValue, String actorId, LocalDateTime now) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("운영자 계정이 비었다");
        }
        if (!key.validate(newValue).valid() || !key.validateTransition(value, newValue).valid()) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        OperationSettingHistory history =
                new OperationSettingHistory(
                        new SettingChangeResult(key, value, newValue), now, actorId);
        this.value = newValue;
        this.updatedAt = now;
        return history;
    }

    SettingKey key() {
        return key;
    }

    String value() {
        return value;
    }
}
