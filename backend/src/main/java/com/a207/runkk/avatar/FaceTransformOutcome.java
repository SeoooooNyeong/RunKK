package com.a207.runkk.avatar;

import java.util.Optional;

/**
 * 얼굴 변환 결과: 성공이면 SdFacePartImages, 실패면 FailureReason(설계 6.1.8 FaceTransformGateway).
 *
 * @param images 성공 때 부위별 PNG, 실패면 null
 * @param failure 실패 사유, 성공이면 null
 */
public record FaceTransformOutcome(SdFacePartImages images, FailureReason failure) {

    public FaceTransformOutcome {
        if ((images == null) == (failure == null)) {
            throw new IllegalArgumentException("성공 이미지와 실패 사유 중 하나만 있어야 한다");
        }
    }

    public static FaceTransformOutcome success(SdFacePartImages images) {
        return new FaceTransformOutcome(images, null);
    }

    public static FaceTransformOutcome failure(FailureReason reason) {
        return new FaceTransformOutcome(null, reason);
    }

    public boolean isSuccess() {
        return images != null;
    }

    public Optional<FailureReason> failureReason() {
        return Optional.ofNullable(failure);
    }
}
