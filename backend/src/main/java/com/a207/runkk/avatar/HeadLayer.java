package com.a207.runkk.avatar;

/**
 * 층별 머리 엔드포인트가 내주는 층(GET /avatar/heads/{회원}/{작업}/layers/{이름}). 앱이 실제로 합성에 쓰는 여섯 장만 둔다: 정면
 * 얼굴·눈·헤어(얼굴 원과 Rive 정면), 옆모습 얼굴·눈·헤어(Rive 옆모습). 완성 원본(frontImage·rightImage)과
 * mobile.renderFaceImage는 앱이 쓰지 않아 내주지 않는다(S15P31A207-300).
 */
public enum HeadLayer {
    FACE("face"),
    EYES("eyes"),
    HAIR("hair"),
    RIGHT_FACE("rightFace"),
    RIGHT_EYES("rightEyes"),
    RIGHT_HAIR("rightHair");

    private final String pathName;

    HeadLayer(String pathName) {
        this.pathName = pathName;
    }

    /** 주소에 쓰는 이름(face, eyes, hair, rightFace, rightEyes, rightHair). */
    public String pathName() {
        return pathName;
    }

    /** 주소 이름 → 층. 모르는 이름이면 IllegalArgumentException(400). */
    public static HeadLayer fromName(String name) {
        for (HeadLayer layer : values()) {
            if (layer.pathName.equals(name)) return layer;
        }
        throw new IllegalArgumentException("모르는 머리 층");
    }
}
