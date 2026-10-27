package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;

/**
 * 올린 게시 사진 한 장(설계 6.1.7, FR-211, NFR-042, D-100, D-142). 생성자가 크기 ≤ 1MB → JPEG 머리(FF D8 FF) →
 * JpegMetadataStripper.strip 순서로 검사하고 위치 메타데이터를 지운 바이트만 갖는다. 크기 위반은 TOO_LARGE(413), 형식·구조 위반은
 * UNSUPPORTED_IMAGE(422). 원본 바이트는 이 객체 밖으로 나가지 않는다.
 */
public final class PhotoUpload {

    /** 장당 상한 1MB(D-100). */
    public static final int MAX_BYTES = 1_048_576;

    private final byte[] jpeg;

    public PhotoUpload(byte[] original) {
        if (original == null) {
            throw new BusinessRuleException(ReasonCode.UNSUPPORTED_IMAGE);
        }
        if (original.length > MAX_BYTES) {
            throw new BusinessRuleException(ReasonCode.TOO_LARGE);
        }
        if (original.length < 3
                || (original[0] & 0xFF) != 0xFF
                || (original[1] & 0xFF) != 0xD8
                || (original[2] & 0xFF) != 0xFF) {
            throw new BusinessRuleException(ReasonCode.UNSUPPORTED_IMAGE);
        }
        this.jpeg = JpegMetadataStripper.strip(original);
    }

    /** 위치 메타데이터를 지운 JPEG 바이트(사본). */
    public byte[] jpeg() {
        return jpeg.clone();
    }

    /** 저장할 바이트 수. */
    public int size() {
        return jpeg.length;
    }
}
