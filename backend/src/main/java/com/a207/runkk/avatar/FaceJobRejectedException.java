package com.a207.runkk.avatar;

/** 얼굴 작업 경계에서 공개할 상태와 사유. 응답 본문·사진은 예외에 넣지 않는다. */
public class FaceJobRejectedException extends RuntimeException {
    private final int status;
    private final String reason;

    public FaceJobRejectedException(int status, String reason) {
        super(reason);
        this.status = status;
        this.reason = reason;
    }

    public int status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
