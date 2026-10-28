package com.a207.runkk.crew;

import java.util.List;

/**
 * 게시글 입력(FR-211). 사진 수(0~5)와 빈 글은 BoardPost가 PHOTO_LIMIT·POST_EMPTY(409)로 본다.
 *
 * @param body 본문(0~1,000자), 비울 수 있다
 * @param photos 사진(PhotoUpload가 크기·형식 검사와 위치 메타데이터 제거를 마쳤다)
 */
public record BoardDraft(String body, List<PhotoUpload> photos) {

    public BoardDraft {
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}
