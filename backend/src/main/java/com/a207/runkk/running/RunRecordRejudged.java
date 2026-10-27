package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * v0.10 일회성 재판정으로 무효 기록이 유효가 되었음을 알리는 사건(RunRejudgeService, 사용자 결정). 저장 때 RunRecordSaved로 일어났어야 할 일
 * 가운데 저장 시점에만 쌓이는 값(리워드 적립, 랜드마크 배경 해금)을 멱등하게 다시 맞추라는 뜻이다. 수신자는 알림(푸시)을 보내지 않는다. 대결·크루런 판정,
 * Runimal, 리포트는 이 사건을 받지 않는다(이미 끝난 판정은 바꾸지 않는다). occurredAt은 원래 저장 일시라 리워드 하루 상한을 그날로 센다.
 *
 * @param recordId 기록 ID
 * @param memberId 회원 ID
 * @param distance 집계 거리(초과 구간을 뺀 값)
 * @param occurredAt 원래 저장 일시
 */
public record RunRecordRejudged(
        String recordId, long memberId, Distance distance, LocalDateTime occurredAt)
        implements DomainEvent {}
