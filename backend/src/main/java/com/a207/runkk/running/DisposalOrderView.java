package com.a207.runkk.running;

import java.time.LocalDateTime;

/**
 * 기기가 아직 확인하지 않은 파기 지시(GET /me/disposals, SyncDisposalsUseCase).
 *
 * @param id 지시 ID
 * @param kind 파기 종류
 * @param dueAt 기한
 * @param status 상태
 */
public record DisposalOrderView(
        long id, DisposalKind kind, LocalDateTime dueAt, DisposalStatus status) {}
