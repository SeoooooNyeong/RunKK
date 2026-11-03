package com.a207.runkk.web.error;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import java.sql.SQLException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 예외를 응답 코드와 화면 문구로 바꾼다(설계 6.1.13). 예외 4종(13.1): BusinessRuleException → 409 + 사유 코드,
 * PermissionDeniedException → 403 "권한 없음", IllegalArgumentException·요청 형식 오류 → 400,
 * IllegalStateException → 409. 같은 행을 동시에 바꾼 충돌(낙관적 잠금·고유 제약 경합)은 409 "다시 시도해 주세요"다(1단계 리뷰 L10, 설계
 * 갱신 요청 U6). 내부 메시지는 응답에 싣지 않는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** PostgreSQL·H2 고유 제약 위반 SQLSTATE. */
    private static final String UNIQUE_VIOLATION = "23505";

    /** 외래 키 위반 SQLSTATE: 참조하던 행이 동시에 지워진 경합이라 409로 둔다. */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    /** 409가 아닌 사유 코드. */
    private static final Map<ReasonCode, HttpStatus> STATUS_BY_REASON =
            Map.of(
                    ReasonCode.AUTH_FAILED, HttpStatus.UNAUTHORIZED,
                    ReasonCode.PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    ReasonCode.TEMPORARILY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    ReasonCode.TOO_MANY_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS,
                    ReasonCode.TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                    ReasonCode.UNSUPPORTED_IMAGE, HttpStatus.UNPROCESSABLE_ENTITY);

    /**
     * 업무 규칙 거부는 409가 기본이다. 로그인 실패(AUTH_FAILED 401, PROVIDER_UNAVAILABLE 503)와 서버 일시
     * 장애(TEMPORARILY_UNAVAILABLE 503), 시도 제한(429), 게시 사진 크기·형식(TOO_LARGE 413, UNSUPPORTED_IMAGE
     * 422, 설계 06-06)만 그 뜻에 맞는 상태로 준다(앱–서버 대조 B 단계: 로그인 실패도 공통 오류 형식).
     */
    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(STATUS_BY_REASON.getOrDefault(e.reason(), HttpStatus.CONFLICT))
                .body(new ErrorResponse(e.reason().name(), e.reason().message()));
    }

    /** 조회 대상 없음(W31)은 사유 코드와 문구는 그대로 404다. */
    @ExceptionHandler(NotFoundRuleException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NotFoundRuleException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
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
        MissingRequestHeaderException.class,
        MissingServletRequestPartException.class
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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleConcurrentUpdate(RuntimeException e) {
        log.info("concurrent_update type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONCURRENT_UPDATE", "다시 시도해 주세요"));
    }

    /**
     * 고유 제약 경합(SQLSTATE 23505)과 동시 삭제로 생긴 외래 키 위반(23503)만 409 "다시 시도해 주세요"다. NOT NULL·길이 같은 다른 무결성
     * 위반은 프로그래밍 오류라 500으로 두고 오류 로그를 남긴다(코드 리뷰 2차 L2).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleIntegrityViolation(DataIntegrityViolationException e) {
        // 본문 없는 500: 앱은 기존처럼 일반 서버 오류로 처리한다
        String state = sqlStateOf(e);
        if (UNIQUE_VIOLATION.equals(state) || FOREIGN_KEY_VIOLATION.equals(state)) {
            return handleConcurrentUpdate(e);
        }
        log.error("integrity_violation sqlState={}", sqlStateOf(e), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build(); // 새 오류 code를 만들지 않는다
    }

    private static String sqlStateOf(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** 업로드 크기 초과(셀카 1MB, D-59). 본문은 로그에 남기지 않는다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        log.info("upload_too_large");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("TOO_LARGE", "사진이 너무 커요"));
    }

    /** 지원하지 않는 형식(셀카는 JPEG·PNG만). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMedia(HttpMediaTypeNotSupportedException e) {
        log.info("unsupported_media");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("UNSUPPORTED_IMAGE", "지원하지 않는 형식"));
    }
}
