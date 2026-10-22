package com.a207.runkk.crew;

import java.time.LocalDateTime;

/**
 * 크루 생성 결과(자료사전 크루생성결과: 크루ID + 초대코드 + 완료).
 *
 * @param crewId 크루 ID
 * @param inviteCode 초대 코드
 * @param inviteCodeExpiresAt 초대 코드 만료 일시
 */
public record CrewCreateResult(long crewId, String inviteCode, LocalDateTime inviteCodeExpiresAt) {}
