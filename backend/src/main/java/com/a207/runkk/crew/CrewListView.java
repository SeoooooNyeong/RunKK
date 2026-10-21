package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import java.util.List;

/**
 * 크루 목록(자료사전 크루목록, FR-117). 크루 밖 열람 범위 항목만 담는다(Q-45, W-16). 크루가 없는 러너면 추천을 함께 싣는다.
 *
 * @param crews 공개 크루, 최근 생성순
 * @param recommendations 크루 없는 러너의 추천, 크루가 있으면 null
 */
public record CrewListView(List<Item> crews, RecommendationView recommendations) {

    public CrewListView {
        crews = List.copyOf(crews);
    }

    /**
     * 목록 한 줄.
     *
     * @param row 크루 정보
     * @param leaderName 크루장 표시명
     * @param tagMatches 열람자 태그와 겹치는 수
     */
    public record Item(CrewListRow row, DisplayName leaderName, int tagMatches) {}
}
