package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 설계 v0.11 사유 코드(F.2.3, 06-01 ReasonCode, 06-06 ApiExceptionHandler). TC-6198·TC-6199. */
@DisplayName("v0.11 사유 코드")
class ReasonCodeV011Test {

    /** F.2.3 새 사유 16개와 화면 문구(TC-6198). */
    static final Map<String, String> NEW_409 =
            Map.ofEntries(
                    Map.entry("ALREADY_LINKED", "이미 연동한 계정"),
                    Map.entry("MANAGER_LIMIT", "운영진은 2명까지 지정할 수 있음"),
                    Map.entry("PIN_LIMIT", "공지는 3개까지 고정할 수 있음"),
                    Map.entry("SCHEDULE_INVALID", "일정 입력 오류"),
                    Map.entry("SCHEDULE_FULL", "정원이 찼음"),
                    Map.entry("POLL_INVALID", "투표 입력 오류"),
                    Map.entry("POLL_CLOSED", "마감된 투표"),
                    Map.entry("POST_EMPTY", "내용이나 사진이 필요함"),
                    Map.entry("PHOTO_LIMIT", "사진은 5장까지"),
                    Map.entry("CHAT_EMPTY", "빈 메시지"),
                    Map.entry("CHAT_MESSAGE_NOT_FOUND", "메시지 없음"),
                    Map.entry("REPORT_TARGET_NOT_FOUND", "신고할 대상이 없음"),
                    Map.entry("ALREADY_REPORTED", "이미 신고함"),
                    Map.entry("REPORT_NOT_FOUND", "신고 없음"),
                    Map.entry("REPORT_DECIDED", "이미 처리한 신고"),
                    Map.entry("BACKGROUND_LOCKED", "아직 해금하지 않은 배경"));

    @Test
    @DisplayName("새 사유 16개가 설계 문구를 가진다")
    void newReasons_haveDesignMessages() {
        NEW_409.forEach(
                (name, message) ->
                        assertThat(ReasonCode.valueOf(name).message()).isEqualTo(message));
    }

    @Test
    @DisplayName("게시 사진 거부 사유 TOO_LARGE·UNSUPPORTED_IMAGE가 사유 코드다(06-04 PhotoUpload, 06-06)")
    void photoUploadReasons_exist() {
        assertThat(ReasonCode.TOO_LARGE.message()).isEqualTo("사진이 너무 커요");
        assertThat(ReasonCode.UNSUPPORTED_IMAGE.message()).isEqualTo("지원하지 않는 형식");
    }

    @Test
    @DisplayName("ALREADY_MEMBER 문구는 FR-198 '이미 가입된 계정'이다(D-155 (3))")
    void alreadyMember_usesUnifiedMessage() {
        assertThat(ReasonCode.ALREADY_MEMBER.message()).isEqualTo("이미 가입된 계정");
    }

    @Test
    @DisplayName("쓰지 않게 되는 상수도 지우지 않는다(F.2.3, TC-6199)")
    void retiredReasons_stay() {
        assertThat(Arrays.stream(ReasonCode.values()).map(Enum::name))
                .contains("TAG_INVALID", "INTRO_TOO_LONG", "VOTE_CLOSED", "NOT_CREW_MEMBER");
    }

    @ParameterizedTest
    @EnumSource(ReasonCode.class)
    @DisplayName("모든 사유 코드는 빈 문구가 없다")
    void everyReason_hasMessage(ReasonCode reason) {
        assertThat(reason.message()).isNotBlank();
    }
}
