package com.a207.runkk.avatar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Node 얼굴 결과: 정면 3레이어(v1.2)와 W9 Rive 렌더러용 옆모습 3레이어·정면/옆 원본 2장, 앱 합성 정보(mobile: neckMask·skinColor
 * 등을 그대로 넘긴다). 모든 PNG는 1024×1024 공통 좌표의 Data URL이다. 원본 셀카는 포함하지 않는다. W9(S15P31A207-300) 신규 필드는 ai가
 * 주지 않으면(예전 얼굴) 없고 JSON에서 빠진다(contract-W8.md 6.2).
 *
 * @param frontImage 완성된 정면 머리 원본, 없으면 null
 * @param rightImage 완성된 오른쪽 옆모습 머리 원본, 없으면 null
 * @param rightFaceImage 옆모습 피부 기반·코·입·귀·목, 없으면 null
 * @param rightEyesImage 옆모습 눈·속눈썹, 없으면 null
 * @param rightHairImage 옆모습 머리카락·눈썹·안경, 없으면 null
 * @param rightLayerLayout 옆모습 레이어 배치 정보(ai가 준 그대로), 없으면 null
 */
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
        String createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String frontImage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rightImage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rightFaceImage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rightEyesImage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rightHairImage,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> rightLayerLayout) {

    /** Jackson은 전체 항목 생성자로 읽는다(빠진 항목은 null). */
    @JsonCreator
    public GeneratedHead {}

    /** 정면 3레이어만 있는 결과(v1.2). */
    public GeneratedHead(
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
        this(
                headId,
                userId,
                faceImage,
                eyesImage,
                hairImage,
                traits,
                hairLength,
                alignment,
                mobile,
                createdAt,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Override
    public String toString() {
        return "GeneratedHead[images=***]";
    }
}
