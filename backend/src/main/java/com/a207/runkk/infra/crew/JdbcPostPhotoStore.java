package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.PostPhotoStore;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * crew.PostPhotoStore의 PostgreSQL 구현(설계 5.1.1 infra.crew, D-100). 사진 바이트는 crew_photo.jpeg(BYTEA)에
 * 둔다. 메타 행은 crew 패키지의 PostPhoto 엔티티가 먼저 만들고, 이 저장소는 같은 트랜잭션·같은 연결에서 jpeg 칼럼만 쓰고 읽는다. 바이트를 엔티티에 두지
 * 않아 앨범·목록 조회가 바이트를 읽지 않는다.
 */
@Repository
public class JdbcPostPhotoStore implements PostPhotoStore {

    private final EntityManager entityManager;

    public JdbcPostPhotoStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(long photoId, byte[] jpeg) {
        entityManager.flush();
        int updated =
                entityManager
                        .createNativeQuery("update crew_photo set jpeg = :jpeg where id = :id")
                        .setParameter("jpeg", jpeg)
                        .setParameter("id", photoId)
                        .executeUpdate();
        if (updated != 1) {
            throw new IllegalStateException("사진 메타 행이 없다: " + photoId);
        }
    }

    @Override
    public Optional<byte[]> load(long photoId) {
        List<?> rows =
                entityManager
                        .createNativeQuery("select jpeg from crew_photo where id = :id")
                        .setParameter("id", photoId)
                        .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        byte[] jpeg = (byte[]) rows.get(0);
        return jpeg.length == 0 ? Optional.empty() : Optional.of(jpeg);
    }

    @Override
    public void deleteByPost(long postId) {
        entityManager.flush();
        entityManager
                .createNativeQuery("delete from crew_photo where post_id = :post")
                .setParameter("post", postId)
                .executeUpdate();
    }
}
