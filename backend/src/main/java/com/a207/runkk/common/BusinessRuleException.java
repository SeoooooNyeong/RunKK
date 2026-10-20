package com.a207.runkk.common;

/** 업무 규칙 거부(설계 13.1 예외 4종 중 하나). ApiExceptionHandler가 409 + 사유 코드로 바꾼다. */
public class BusinessRuleException extends RuntimeException {

    private final ReasonCode reason;

    public BusinessRuleException(ReasonCode reason) {
        super(reason.message());
        this.reason = reason;
    }

    public ReasonCode reason() {
        return reason;
    }
}
