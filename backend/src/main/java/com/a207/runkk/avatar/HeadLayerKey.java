package com.a207.runkk.avatar;

import java.util.Objects;

/**
 * 머리 층 하나를 가리키는 값(v0.14 S15P31A207-316, 31장 2차안 D13). 형식 판은 컨트롤러가 다루고 여기에는 넣지 않는다.
 *
 * @param ownerId 머리 주인 회원 ID
 * @param jobId 셀카 작업 ID
 * @param layer 층
 * @param size 공통 캔버스 한 변 기준 크기(256·512·1024). 아니면 IllegalArgumentException(400)
 */
public record HeadLayerKey(long ownerId, String jobId, HeadLayer layer, int size) {

    public HeadLayerKey {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(layer, "layer");
        if (!HeadLayerCache.SIZES.contains(size)) {
            throw new IllegalArgumentException("머리 층 크기 오류");
        }
    }
}
