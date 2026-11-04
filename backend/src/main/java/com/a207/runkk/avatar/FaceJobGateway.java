package com.a207.runkk.avatar;

/** AI 얼굴 작업 포트. 회원 ID는 서버 인증에서 얻은 값만 전달한다. */
public interface FaceJobGateway {
    FaceJob submit(long memberId, byte[] image);

    FaceJob status(long memberId, String jobId);

    GeneratedHead result(long memberId, String jobId);

    GeneratedHead latest(long memberId);

    AvatarBody body(String bodyId);
}
