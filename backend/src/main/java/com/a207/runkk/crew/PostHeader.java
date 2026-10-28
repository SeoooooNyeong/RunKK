package com.a207.runkk.crew;

import java.time.LocalDateTime;

/**
 * 종류 정보가 없는 게시글 줄의 공통 머리(PostSummary.of의 입력, BE5-B-2).
 *
 * @param postId 게시글 ID
 * @param kind 게시 종류
 * @param authorId 작성자, 탈퇴했으면 null
 * @param body 본문
 * @param writtenAt 작성 일시
 */
record PostHeader(
        long postId, PostKind kind, Long authorId, String body, LocalDateTime writtenAt) {}
