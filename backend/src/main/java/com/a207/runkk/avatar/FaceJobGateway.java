package com.a207.runkk.avatar;

/** AI 얼굴 작업 포트. 회원 ID는 서버 인증에서 얻은 값만 전달한다. */
public interface FaceJobGateway {
    /**
     * 셀카 접수. attempt는 같은 사진의 명시적 재시도 번호(1부터)다. 같은 번호 재전송은 ai가 같은 작업을 돌려주고, 번호를 올리면 새 작업을 만든다(DV-9).
     */
    FaceJob submit(long memberId, byte[] image, int attempt);

    FaceJob status(long memberId, String jobId);

    GeneratedHead result(long memberId, String jobId);

    GeneratedHead latest(long memberId);

    AvatarBody body(String bodyId);
}
