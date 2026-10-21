package com.a207.runkk.avatar;

/**
 * 셀카 → SD 얼굴 부위 변환 포트(설계 6.1.8, D-23, D-59). 구현은 infra.integration.ai.AiServerFaceGateway. 회원 식별자
 * 매개변수가 없다(NFR-024). 이미지는 어디에도 저장하지 않는다.
 */
public interface FaceTransformGateway {

    /** 성공이면 부위별 PNG, 실패면 FailureReason. 예외를 던지지 않는다. */
    FaceTransformOutcome transform(byte[] image);
}
