package com.a207.runkk.common;

/** 권한 거부(설계 13.1 예외 4종 중 하나). ApiExceptionHandler가 403 "권한 없음"으로 바꾼다. */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
