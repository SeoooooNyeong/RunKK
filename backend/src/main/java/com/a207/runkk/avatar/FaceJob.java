package com.a207.runkk.avatar;

/** FAILED도 정상 조회 응답이며 사유는 error에 있다. */
public record FaceJob(String jobId, Status status, String headId, Error error) {
    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record Error(String code, String message) {
        public Error {
            if (code == null
                    || code.isBlank()
                    || code.length() > 100
                    || (message != null && message.length() > 500)) {
                throw new IllegalArgumentException("얼굴 작업 오류 형식 오류");
            }
        }
    }

    public FaceJob {
        if (jobId == null || !jobId.matches("[A-Za-z0-9_.:-]{1,64}") || status == null) {
            throw new IllegalArgumentException("얼굴 작업 형식 오류");
        }
        if (status == Status.FAILED && (error == null || error.code() == null)) {
            throw new IllegalArgumentException("실패 작업에는 사유가 필요하다");
        }
        if ((headId != null && !headId.matches("[A-Za-z0-9_.:-]{1,64}"))
                || (status == Status.SUCCEEDED && headId == null)) {
            throw new IllegalArgumentException("완료 작업에는 머리 ID가 필요하다");
        }
    }
}
