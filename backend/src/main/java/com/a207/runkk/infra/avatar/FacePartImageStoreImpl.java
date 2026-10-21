package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.FacePartImageStore;
import com.a207.runkk.avatar.SdFacePartImages;
import com.a207.runkk.avatar.SdFaceParts;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * avatar.FacePartImageStore 구현(설계 5.1.1 infra.avatar "SD 부위 이미지 저장소", D-59). 저장 키는
 * "face/{회원}/{묶음}/{부위}"이고 묶음은 저장할 때마다 새 UUID다. 회원마다 가장 최근 묶음만 남긴다. face_part_image 테이블만 쓴다.
 */
@Repository
public class FacePartImageStoreImpl implements FacePartImageStore {

    private static final String KEY_ROOT = "face/";

    private final EntityManager entityManager;
    private final Clock clock;

    public FacePartImageStoreImpl(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public SdFaceParts save(long memberId, SdFacePartImages images) {
        deleteByMember(memberId);
        String set = KEY_ROOT + memberId + "/" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        Map<FacePart, String> keys = new EnumMap<>(FacePart.class);
        images.images()
                .forEach(
                        (part, png) -> {
                            String key = set + "/" + part.name();
                            entityManager.persist(
                                    new FacePartImage(key, memberId, part.name(), png, now));
                            keys.put(part, key);
                        });
        return new SdFaceParts(keys);
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from FacePartImage i where i.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
