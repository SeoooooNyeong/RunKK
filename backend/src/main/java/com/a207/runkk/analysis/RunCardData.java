package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 러닝 카드 값(설계 6.1.4 RecordQueryService.cardData, FR-068, D-22). 카드 이미지는 앱이 그린다. 본인 기록만이라 뒷면 경로를 늘
 * 담고, 외부 공유 때 뒷면을 넣을지는 courseMapShared로 앱이 정한다(FR-069).
 *
 * @param recordId 기록 ID
 * @param cardKind 카드 종류 라벨
 * @param startedAt 날짜
 * @param distance 거리
 * @param elapsed 경과 시간
 * @param averagePace 평균 페이스
 * @param averageHeartRate 평균 심박, 없으면 null
 * @param newRecords 신기록 문구
 * @param route 뒷면 경로 좌표(파기 뒤 빈 목록)
 * @param courseMapShared 코스지도공유 설정
 * @param weather 날씨(없으면 null, 카드 앞면, v0.14 D-235)
 */
public record RunCardData(
        String recordId,
        String cardKind,
        LocalDateTime startedAt,
        Distance distance,
        Duration elapsed,
        Pace averagePace,
        Integer averageHeartRate,
        List<String> newRecords,
        List<GeoPoint> route,
        boolean courseMapShared,
        RecordDetailView.WeatherInfo weather) {}
