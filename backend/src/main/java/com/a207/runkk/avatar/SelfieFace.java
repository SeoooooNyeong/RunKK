package com.a207.runkk.avatar;

/**
 * 셀카에서 만든 SD 얼굴 부위를 가진 얼굴(설계 6.1.8).
 *
 * @param parts 부위별 이미지 저장 키
 */
public record SelfieFace(SdFaceParts parts) implements Face {

    static final String PREFIX = "selfie:";

    /** "selfie:" + 부위 이미지 묶음 저장 위치 키. */
    @Override
    public String renderKey() {
        return PREFIX + parts.setKey();
    }
}
