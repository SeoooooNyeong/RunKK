package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import java.util.List;

/**
 * 크루 목록(자료사전 크루목록, FR-117). 크루 밖 열람 범위 항목만 담는다(Q-45, W-16). v0.11에서 크루 추천과 태그 일치 수를 지웠다(FR-118 삭제,
 * D-91).
 *
 * @param crews 공개 크루, 최근 생성순
 * @param nextCursor 다음 페이지 커서(크루 ID), 마지막 페이지거나 나누지 않았으면 null
 */
public record CrewListView(List<Item> crews, Long nextCursor) {

    public CrewListView {
        crews = List.copyOf(crews);
    }

    /**
     * 목록 한 줄.
     *
     * @param row 크루 정보
     * @param leaderName 크루장 표시명
     */
    public record Item(CrewListRow row, DisplayName leaderName) {}
}
