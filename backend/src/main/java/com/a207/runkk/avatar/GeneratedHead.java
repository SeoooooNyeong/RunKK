package com.a207.runkk.avatar;

import java.util.Map;

/** Node v1.2의 얼굴 3레이어와 앱 합성 정보. 원본 셀카는 포함하지 않는다. */
public record GeneratedHead(
        String headId,
        String userId,
        String faceImage,
        String eyesImage,
        String hairImage,
        Map<String, Object> traits,
        String hairLength,
        Map<String, Object> alignment,
        Map<String, Object> mobile,
        String createdAt) {
    @Override
    public String toString() {
        return "GeneratedHead[images=***]";
    }
}
