package com.a207.runkk.audit;

/**
 * 감사 기록 한 건의 입력(설계 6.1.11). 다른 패키지가 AuditService.record에 넘긴다.
 *
 * @param actorId 회원 ID 또는 운영자 계정
 * @param actorRole 행위자 역할
 * @param action 행위 종류
 * @param targetId 대상 ID
 * @param before 변경 전 값(없을 수 있음)
 * @param after 변경 후 값(없을 수 있음)
 */
public record AuditEntry(
        String actorId,
        ActorRole actorRole,
        AuditAction action,
        String targetId,
        String before,
        String after) {

    public AuditEntry {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("행위자 ID가 비었다");
        }
        if (actorRole == null || action == null) {
            throw new IllegalArgumentException("행위자 역할·행위 종류가 비었다");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("대상 ID가 비었다");
        }
    }
}
