package com.a207.runkk.web.error;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외를 응답 코드와 화면 문구로 바꾼다(설계 6.1.13). 예외 4종(13.1): BusinessRuleException → 409 + 사유 코드,
 * PermissionDeniedException → 403 "권한 없음", IllegalArgumentException·요청 형식 오류 → 400,
 * IllegalStateException → 409. 같은 행을 동시에 바꾼 충돌(낙관적 잠금·고유 제약 경합)은 409 "다시 시도해 주세요"다(1단계 리뷰 L10, 설계
 * 갱신 요청 U6). 내부 메시지는 응답에 싣지 않는다.
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

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
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

    @ExceptionHandler({
        OptimisticLockingFailureException.class,
        DataIntegrityViolationException.class
    })
    ResponseEntity<ErrorResponse> handleConcurrentUpdate(RuntimeException e) {
        log.info("concurrent_update type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONCURRENT_UPDATE", "다시 시도해 주세요"));
    }
}
