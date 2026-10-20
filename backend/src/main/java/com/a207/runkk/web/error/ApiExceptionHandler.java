package com.a207.runkk.web.error;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외를 응답 코드와 화면 문구로 바꾼다(설계 6.1.13). 예외 4종(13.1)만 다룬다: BusinessRuleException → 409 + 사유 코드,
 * PermissionDeniedException → 403 "권한 없음", IllegalArgumentException → 400, IllegalStateException →
 * 409. 내부 메시지는 응답에 싣지 않는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.reason().name(), e.reason().message()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    ResponseEntity<ErrorResponse> handlePermissionDenied(PermissionDeniedException e) {
        log.info("permission_denied reason={}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("PERMISSION_DENIED", "권한 없음"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorResponse> handleIllegalArgument(Exception e) {
        log.debug("bad_request type={}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "잘못된 요청"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.info("invalid_state reason={}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATE", "처리할 수 없는 상태"));
    }
}
