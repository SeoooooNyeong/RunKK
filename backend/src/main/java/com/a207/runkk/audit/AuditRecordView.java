package com.a207.runkk.audit;

import java.time.LocalDateTime;

/**
 * 관리툴에 내주는 감사 기록 한 건의 읽기 전용 투영(설계 6.1.11, D-32).
 *
 * @param at 기록 일시
 * @param actorId 행위자 ID
 * @param actorRole 행위자 역할
 * @param action 행위 종류
 * @param targetId 대상 ID
 * @param before 변경 전 값
 * @param after 변경 후 값
 */
public record AuditRecordView(
        LocalDateTime at,
        String actorId,
        ActorRole actorRole,
        AuditAction action,
        String targetId,
        String before,
        String after) {}
