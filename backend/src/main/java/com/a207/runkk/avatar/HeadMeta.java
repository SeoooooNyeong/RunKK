package com.a207.runkk.avatar;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 머리 메타데이터(GET /avatar/heads/{회원}/{작업}/meta). 이미지 데이터는 없다. 층 그림은 …/layers/{이름}으로 따로 받는다.
 *
 * @param format 머리 형식 판({@link HeadFormat#VERSION}, 층·메타 ETag와 층 주소에도 들어간다)
 * @param canvas 층 원본의 공통 캔버스 한 변(1024). 층을 줄여 받아도 좌표는 이 캔버스 비율 그대로다
 * @param mobile ai가 준 앱 합성 정보에서 이미지 값(Data URL)을 뺀 것(neckMask·skinColor 등)
 * @param layers 이 머리에 있는 층 이름(정면 셋은 늘 있고, 예전 머리는 옆모습 셋이 없을 수 있다)
 * @param rightLayerLayout 옆모습 레이어 배치 정보, 없으면 null(JSON에서 빠진다)
 * @param layerUrls 크기("256"·"512"·"1024") → 층 이름 → 층 상대 주소(형식 판 포함, {@link HeadFormat#layerUrl}). 이
 *     머리에 있는 층만 있다. 앱은 이 주소를 그대로 쓴다(v0.14)
 */
public record HeadMeta(
        String format,
        String headId,
        int canvas,
        Map<String, Object> traits,
        String hairLength,
        Map<String, Object> alignment,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> rightLayerLayout,
        Map<String, Object> mobile,
        List<String> layers,
        String createdAt,
        Map<String, Map<String, String>> layerUrls) {}
