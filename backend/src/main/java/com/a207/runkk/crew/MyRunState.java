package com.a207.runkk.crew;

import java.util.Optional;

/**
 * 열람자의 크루런 참여 상태(v0.14 S15P31A207-314, S15P31A207-314). 크루런 목록·상세가 내주고, 앱은 RUNNING 카드의 단추를 이것으로
 * 고른다(ENDED → 결과 보기, RUNNING → 이어 달리기, NONE → 중간 참여하기).
 */
public enum MyRunState {
    /** 참여하지 않았거나 아직 진행 참가자가 아니다(투표·대기실·카운트다운 포함). */
    NONE,
    /** 진행 참가자로 측정 중이다. */
    RUNNING,
    /** 진행 참가자였고 자기 측정을 끝냈다(크루런 마감으로 끝난 경우 포함). */
    ENDED;

    /** 열람자의 참여로 상태를 정한다. */
    public static MyRunState of(Optional<CrewRunParticipation> participation) {
        return participation
                .filter(CrewRunParticipation::isRunner)
                .map(p -> p.hasEnded() ? ENDED : RUNNING)
                .orElse(NONE);
    }
}
