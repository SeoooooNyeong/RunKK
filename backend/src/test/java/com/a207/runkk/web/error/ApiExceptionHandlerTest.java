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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "ALREADY_LINKED",
                "MANAGER_LIMIT",
                "PIN_LIMIT",
                "SCHEDULE_INVALID",
                "SCHEDULE_FULL",
                "POLL_INVALID",
                "POLL_CLOSED",
                "POST_EMPTY",
                "PHOTO_LIMIT",
                "CHAT_EMPTY",
                "CHAT_MESSAGE_NOT_FOUND",
                "REPORT_TARGET_NOT_FOUND",
                "ALREADY_REPORTED",
                "REPORT_NOT_FOUND",
                "REPORT_DECIDED",
                "BACKGROUND_LOCKED"
            })
    @org.junit.jupiter.api.DisplayName("TC-6200: v0.11 새 사유 16개는 모두 409와 사유 코드·문구다")
    void newV011Reasons_become409(String name) {
        ReasonCode reason = ReasonCode.valueOf(name);
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessRule(new BusinessRuleException(reason));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(name, reason.message()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6200: 상태가 409가 아닌 사유 코드(STATUS_BY_REASON)")
    void nonConflictReasons_mapToTheirStatus() {
        assertThat(statusOf(ReasonCode.UNSUPPORTED_IMAGE))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(statusOf(ReasonCode.TOO_LARGE)).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(statusOf(ReasonCode.TOO_MANY_ATTEMPTS)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(statusOf(ReasonCode.AUTH_FAILED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf(ReasonCode.PROVIDER_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf(ReasonCode.TEMPORARILY_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ResponseEntity<ErrorResponse> image =
                handler.handleBusinessRule(new BusinessRuleException(ReasonCode.UNSUPPORTED_IMAGE));
        assertThat(image.getBody()).isEqualTo(new ErrorResponse("UNSUPPORTED_IMAGE", "지원하지 않는 형식"));
    }

    private org.springframework.http.HttpStatusCode statusOf(ReasonCode reason) {
        return handler.handleBusinessRule(new BusinessRuleException(reason)).getStatusCode();
    }

    @Test
    void permissionDenied_becomes403() {
        ResponseEntity<ErrorResponse> response =
                handler.handlePermissionDenied(new PermissionDeniedException("화이트리스트 밖"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("PERMISSION_DENIED", "권한 없음"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "크루 밖 두 경로(구현 결정): PermissionDeniedException은 403 PERMISSION_DENIED, NOT_CREW_MEMBER는"
                    + " 409 그대로")
    void outsider_twoPathsKeepTheirStatus() {
        ResponseEntity<ErrorResponse> write =
                handler.handlePermissionDenied(new PermissionDeniedException("크루 밖 글 쓰기"));
        ResponseEntity<ErrorResponse> chat =
                handler.handleBusinessRule(new BusinessRuleException(ReasonCode.NOT_CREW_MEMBER));
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getBody().code()).isEqualTo("PERMISSION_DENIED");
        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(chat.getBody())
                .isEqualTo(
                        new ErrorResponse("NOT_CREW_MEMBER", ReasonCode.NOT_CREW_MEMBER.message()));
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
        ResponseEntity<ErrorResponse> deletedMeanwhile = // 동시 삭제로 생긴 외래 키 위반
                handler.handleIntegrityViolation(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "fk", new java.sql.SQLException("foreign key", "23503")));
        assertThat(deletedMeanwhile.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
