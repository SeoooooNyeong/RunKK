package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.List;

/**
 * 유명인 기록 입력(자료사전 유명인기록입력, FR-170). 실명·사진은 받지 않는다(FR-171).
 *
 * @param anonymousName 익명 닉네임
 * @param distance 기록 거리
 * @param finishTime 완주 시간
 * @param splits 구간 기록(경과 시간, 누적 거리). 없으면 빈 목록
 * @param sourceName 출처명
 * @param sourceUrl 출처 주소, 없으면 null
 * @param realNameConsent 실명 등록 동의, 익명이면 null
 * @param figureKey 앱 전용 캐릭터 그림 키("lee" 등), 없으면 null(공통 셀럽 그림, 2026-10-31 결정)
 */
public record CelebrityInput(
        String anonymousName,
        Distance distance,
        Duration finishTime,
        List<TrackSample> splits,
        String sourceName,
        String sourceUrl,
        RealNameConsent realNameConsent,
        String figureKey) {

    public CelebrityInput {
        splits = splits == null ? List.of() : List.copyOf(splits);
    }

    /** 전용 캐릭터 그림이 없는 입력. */
    public CelebrityInput(
            String anonymousName,
            Distance distance,
            Duration finishTime,
            List<TrackSample> splits,
            String sourceName,
            String sourceUrl,
            RealNameConsent realNameConsent) {
        this(
                anonymousName,
                distance,
                finishTime,
                splits,
                sourceName,
                sourceUrl,
                realNameConsent,
                null);
    }

    /** 실명 동의 없는(익명) 입력. */
    public CelebrityInput(
            String anonymousName,
            Distance distance,
            Duration finishTime,
            List<TrackSample> splits,
            String sourceName,
            String sourceUrl) {
        this(anonymousName, distance, finishTime, splits, sourceName, sourceUrl, null, null);
    }
}
