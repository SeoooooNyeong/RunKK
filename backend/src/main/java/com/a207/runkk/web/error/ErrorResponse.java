package com.a207.runkk.web.error;

/**
 * API 오류 응답(설계 5.1.1 web.error).
 *
 * @param code 사유 코드(BusinessRuleException은 ReasonCode 이름)
 * @param message 화면 문구
 */
public record ErrorResponse(String code, String message) {}
