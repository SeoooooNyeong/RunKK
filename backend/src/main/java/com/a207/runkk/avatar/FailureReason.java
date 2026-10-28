package com.a207.runkk.avatar;

/** 얼굴 변환 실패 사유(설계 6.1.8 FaceTransformGateway). ai 서버 FaceRouter의 reason과 같은 이름이다. 화면 문구를 함께 가진다. */
public enum FailureReason {
    NO_FACE("얼굴을 찾지 못했어요"),
    MULTIPLE_FACES("한 사람만 나오게 찍어 주세요"),
    UNSUPPORTED_IMAGE("지원하지 않는 사진 형식이에요"),
    TOO_LARGE("사진이 너무 커요"),
    UPSTREAM_FAILED("지금은 변환할 수 없어요"),
    TIMEOUT("변환 시간이 초과됐어요");

    private final String message;

    FailureReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
