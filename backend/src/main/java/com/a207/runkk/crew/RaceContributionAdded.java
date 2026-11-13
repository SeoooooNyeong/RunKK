package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 유효 러닝 기록 하나가 크루 주간 합산에 처음 더해졌다(피그마 맞춤 W5, 사용자 결정 C7, S15P31A207-296). 기록 저장 커밋 뒤 수신자
 * (WeeklyRaceService.onRunSaved)만 발행하고 0시 재합산이 채운 기록은 발행하지 않는다. crew.chat이 받아 크루 채팅에 러닝 완료 시스템 메시지를
 * 남긴다(crew는 crew.chat을 모른다).
 *
 * @param crewId 기록 시작 일시에 소속이던 크루
 * @param memberId 러닝한 회원
 * @param recordId 기록 ID
 * @param distance 더한 거리
 * @param startedAt 기록 시작 일시
 * @param at 더한 일시
 */
public record RaceContributionAdded(
        long crewId,
        long memberId,
        String recordId,
        Distance distance,
        LocalDateTime startedAt,
        LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
