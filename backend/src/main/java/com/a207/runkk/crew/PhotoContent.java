package com.a207.runkk.crew;

/**
 * 게시 사진 바이트(FR-212). 위치 메타데이터를 지운 JPEG다(D-142).
 *
 * @param photoId 사진 ID
 * @param jpeg JPEG 바이트
 */
public record PhotoContent(long photoId, byte[] jpeg) {}
