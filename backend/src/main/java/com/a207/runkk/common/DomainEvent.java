package com.a207.runkk.common;

import java.time.LocalDateTime;

/** 사건 형식임을 표시하는 표지(D-31). 사건 record는 발행하는 패키지에 둔다. */
public interface DomainEvent {

    LocalDateTime occurredAt();
}
