package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;

/**
 * 크루런 모집글 입력(자료사전 게시내용: 본문, 모집희망일시, 크루런거리, (투표마감일시)). 장소·위치 항목이 없다(FR-132).
 *
 * @param body 본문
 * @param scheduledAt 모집희망일시
 * @param distance 크루런 거리(1·3·5·10km)
 * @param voteDeadline 투표 마감, 비우면 모집희망일시
 */
public record CrewRunDraft(
        String body,
        LocalDateTime scheduledAt,
        StandardDistance distance,
        LocalDateTime voteDeadline) {}
