package com.a207.runkk.crew;

import java.util.Optional;

/**
 * 게시 사진 바이트 저장소 포트(설계 6.1.7, D-100). 구현은 infra.crew.JdbcPostPhotoStore(PostgreSQL crew_photo.jpeg
 * BYTEA). 나중에 객체 저장소로 바꿀 수 있게 포트 뒤에 둔다. 위치 메타데이터를 지운 바이트만 받는다(PhotoUpload, D-142).
 */
public interface PostPhotoStore {

    /** 메타 행(PostPhoto)이 먼저 저장된 사진의 바이트를 쓴다. */
    void save(long photoId, byte[] jpeg);

    Optional<byte[]> load(long photoId);

    /** 그 글의 사진 행을 모두 지운다(글 삭제·작성자 탈퇴, D-100). */
    void deleteByPost(long postId);
}
