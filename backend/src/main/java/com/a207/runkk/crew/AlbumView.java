package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루 앨범(FR-212). 보이는 게시글의 사진, 최신순(photoId 내림차순).
 *
 * @param photos 사진
 * @param nextCursor 다음 페이지 커서(photoId), 없으면 null
 */
public record AlbumView(List<Photo> photos, Long nextCursor) {

    public AlbumView {
        photos = List.copyOf(photos);
    }

    /**
     * 앨범 사진 한 장.
     *
     * @param photoId 사진 ID
     * @param postId 게시글 ID
     * @param postedAt 게시 일시
     */
    public record Photo(long photoId, long postId, LocalDateTime postedAt) {}
}
