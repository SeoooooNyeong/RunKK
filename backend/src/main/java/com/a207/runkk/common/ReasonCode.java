package com.a207.runkk.common;

/**
 * 업무 규칙 거부 사유 코드(설계 6.1, 13.1 "BusinessRuleException(사유 코드 enum)"). 상수는 srs.md 6.3의 거부 사유 문구와 1:1로
 * 대응하고 ApiExceptionHandler가 문구로 바꾼다. 상수는 그 사유를 처음 쓰는 패키지를 구현할 때 더한다.
 */
public enum ReasonCode {
    /** 성향 태그 개수·분류 규칙 위반(srs.md 6.3 1.2, Q-82). */
    TAG_INVALID("태그 오류"),
    /** 운영 기준값 형식·범위 위반(srs.md 6.3 7.2, FR-174). */
    VALUE_INVALID("값 오류"),

    // member (srs.md 6.3 1.1~1.4)
    /** 필수 동의 3종 누락(srs.md 6.3 1.2 규칙 1). */
    CONSENT_MISSING("필수동의누락"),
    /** 만 14세 미만(srs.md 6.3 1.2 규칙 2, FR-004). */
    UNDER_14("만 14세 이상만 가입할 수 있음"),
    /** 닉네임 길이 위반 또는 다른 회원·가상 닉네임과 겹침(srs.md 6.3 1.2 규칙 3 "입력오류(닉네임)", 1.3 "닉네임 오류"). */
    NICKNAME_INVALID("닉네임 오류"),
    /** 프로필 소개 40자 초과(srs.md 6.3 1.3). */
    INTRO_TOO_LONG("소개 길이 초과"),
    /** 설정 값이 설정항목의 허용 값이 아님(srs.md 6.3 1.4 "설정항목 + 거부"). */
    SETTING_INVALID("설정 값 오류"),
    /** 동의 항목을 설정 변경으로 바꾸려 함. 동의는 동의 주기·철회로만 바꾼다(D-36). */
    CONSENT_ITEM_NOT_SETTING("동의 항목은 동의 변경으로 바꿔야 함"),
    /** 소셜 로그인 제공자 무응답(srs.md 6.3 1.1, FR-001). */
    PROVIDER_UNAVAILABLE("로그인 제공자 연결 실패"),
    /** 소셜 로그인 인증 실패(srs.md 6.3 1.1 "로그인실패 + 실패사유"). */
    AUTH_FAILED("인증 실패"),
    /** 가입 티켓이 없거나 15분이 지남(D-19). */
    SIGN_UP_TICKET_INVALID("가입 시간 초과, 다시 로그인해 주세요"),
    /** 이미 가입한 소셜 계정으로 다시 가입함(FR-003). */
    ALREADY_MEMBER("이미 가입한 계정"),

    // running (srs.md 6.3 2.7, 3.2)
    /** 러닝 기록이 없음. */
    RECORD_NOT_FOUND("기록 없음"),
    /** 위치 경로가 파기된 기록(FR-026). */
    ROUTE_PURGED("경로 파기된 기록"),
    /** 파기 지시가 없음(FR-026, FR-029). */
    DISPOSAL_ORDER_NOT_FOUND("파기 지시 없음"),

    // notification (FR-178)
    /** 알림이 없음. */
    NOTIFICATION_NOT_FOUND("알림 없음");

    private final String message;

    ReasonCode(String message) {
        this.message = message;
    }

    /** 화면 문구. */
    public String message() {
        return message;
    }
}
