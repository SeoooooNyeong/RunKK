package com.a207.runkk.common;

/** 읽기 조회 대상이 없음. 사유 코드는 그대로 두고 ApiExceptionHandler가 404로 바꾼다(W31 셀럽 단건 조회). */
public class NotFoundRuleException extends BusinessRuleException {

    public NotFoundRuleException(ReasonCode reason) {
        super(reason);
    }
}
