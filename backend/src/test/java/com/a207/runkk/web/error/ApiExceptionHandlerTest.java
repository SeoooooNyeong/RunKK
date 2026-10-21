package com.a207.runkk.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 설계 6.1.13: BusinessRuleException → 409 + 사유 코드, PermissionDenied → 403, IllegalArgument → 400.
 */
@org.junit.jupiter.api.DisplayName("API 예외 응답 변환")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void businessRule_becomes409WithReasonCodeAndScreenText() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessRule(new BusinessRuleException(ReasonCode.VALUE_INVALID));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("VALUE_INVALID", "값 오류"));
    }

    @Test
    void permissionDenied_becomes403() {
        ResponseEntity<ErrorResponse> response =
                handler.handlePermissionDenied(new PermissionDeniedException("화이트리스트 밖"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("PERMISSION_DENIED", "권한 없음"));
    }

    @Test
    void illegalArgument_becomes400WithoutInternalDetail() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("내부 상세"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("BAD_REQUEST", "잘못된 요청"));
    }

    @Test
    void illegalState_becomes409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalState(new IllegalStateException("처리된 신청"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("INVALID_STATE", "처리할 수 없는 상태"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L10: 낙관적 잠금 충돌과 고유 제약 경합은 409다")
    void concurrentUpdate_becomes409() {
        ResponseEntity<ErrorResponse> optimistic =
                handler.handleConcurrentUpdate(
                        new org.springframework.dao.OptimisticLockingFailureException("version"));
        assertThat(optimistic.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(optimistic.getBody())
                .isEqualTo(new ErrorResponse("CONCURRENT_UPDATE", "다시 시도해 주세요"));
        ResponseEntity<ErrorResponse> duplicate =
                handler.handleIntegrityViolation(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "pk", new java.sql.SQLException("duplicate key", "23505")));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L2: 고유 제약이 아닌 무결성 위반(NOT NULL·길이·외래 키)은 프로그래밍 오류라 500이다")
    void otherIntegrityViolation_becomes500() {
        ResponseEntity<ErrorResponse> notNull =
                handler.handleIntegrityViolation(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "not null", new java.sql.SQLException("null value", "23502")));
        assertThat(notNull.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(notNull.getBody()).isNull();
        assertThat(
                        handler.handleIntegrityViolation(
                                        new org.springframework.dao.DataIntegrityViolationException(
                                                "unknown"))
                                .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
