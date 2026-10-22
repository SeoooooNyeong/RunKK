package com.a207.runkk.policy;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 운영 기준값 한 항목이 바뀌었음을 알린다(설계 6.1.9, D-31). 바뀐 값은 싣지 않는다. 수신자는 OperationPolicy에서 새 값을 읽는다.
 *
 * @param key 바뀐 항목
 * @param at 바뀐 시각
 */
public record OperationSettingChanged(SettingKey key, LocalDateTime at) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
