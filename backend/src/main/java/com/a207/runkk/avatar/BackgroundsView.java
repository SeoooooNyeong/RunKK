package com.a207.runkk.avatar;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 랜드마크 배경 목록 조회 결과(설계 6.1.8 BackgroundsView, FR-202). 순서는 현재 운영 기준값의 개인 랜드마크 순서, 그 뒤에 표에서 빠졌지만 이미
 * 해금한 배경이다(D-107 (4)). web.avatar BackgroundsResponse로 바꾼다.
 *
 * @param selectedKey 고른 배경 키, null이면 기본 배경
 * @param backgrounds 배경
 */
public record BackgroundsView(String selectedKey, List<Item> backgrounds) {

    public BackgroundsView {
        backgrounds = List.copyOf(backgrounds);
    }

    /**
     * 배경 하나.
     *
     * @param key 랜드마크 키
     * @param name 이름(해금한 배경은 해금 때 이름)
     * @param stage 단계 이름(LandmarkStage: DOMESTIC·EAST_ASIA·INTERCONTINENTAL)
     * @param distance 해금 누적 거리
     * @param unlocked 해금됨
     * @param unlockedAt 해금 일시, 잠겼으면 null
     */
    public record Item(
            String key,
            String name,
            String stage,
            Distance distance,
            boolean unlocked,
            LocalDateTime unlockedAt) {}
}
