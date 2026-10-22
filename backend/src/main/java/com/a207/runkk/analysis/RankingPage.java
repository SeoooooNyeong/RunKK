package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 랭킹 조회 한 번에 필요한 부분(상위 줄 + 본인 줄 + 바로 위까지 거리). 캐시는 스냅숏 전체가 아니라 상위 N과 회원별 줄을 따로 두고 이 형태로 읽는다(B5 리뷰:
 * 조회마다 전체 스냅숏 JSON을 풀던 것).
 *
 * @param computedAt 집계 시각
 * @param top 상위 줄(순위순)
 * @param mine 본인(또는 본인 크루) 줄, 없으면 null
 * @param gapToNext 바로 위 순위까지 남은 거리, 없으면 null
 */
public record RankingPage(
        LocalDateTime computedAt, List<RankEntry> top, RankEntry mine, Distance gapToNext) {

    public RankingPage {
        top = List.copyOf(top);
    }
}
