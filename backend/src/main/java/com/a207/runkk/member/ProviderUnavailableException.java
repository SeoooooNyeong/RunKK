package com.a207.runkk.member;

/** 소셜 로그인 제공자가 응답하지 않음(설계 6.1.2, FR-001 "로그인 제공자 연결 실패"). */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
