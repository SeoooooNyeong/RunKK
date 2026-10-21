package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

/**
 * avatar.AvatarRepository의 JPA 구현(설계 5.1.1 infra.avatar). avatar·avatar_face_part·avatar_equipment
 * 테이블만 읽고 쓴다(D-32).
 */
@Repository
public class JpaAvatarRepository implements AvatarRepository {

    private final EntityManager entityManager;

    public JpaAvatarRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Avatar findOrCreate(long memberId) {
        Avatar found = entityManager.find(Avatar.class, memberId);
        return found != null ? found : Avatar.create(memberId);
    }

    /** 있으면 FOR UPDATE로 읽고, 없으면 새로 넣고 돌려준다(첫 생성이 겹치면 기본 키 위반 → 409 "다시 시도"). */
    @Override
    public Optional<Avatar> findForUpdate(long memberId) {
        Avatar found = entityManager.find(Avatar.class, memberId, LockModeType.PESSIMISTIC_WRITE);
        if (found != null) {
            return Optional.of(found);
        }
        Avatar created = Avatar.create(memberId);
        entityManager.persist(created);
        entityManager.flush();
        return Optional.of(created);
    }

    @Override
    public void save(Avatar avatar) {
        if (!entityManager.contains(avatar)) {
            entityManager.merge(avatar);
        }
    }

    @Override
    public Stream<Avatar> streamAll() {
        return entityManager
                .createQuery("select a from Avatar a order by a.memberId", Avatar.class)
                .getResultStream();
    }

    @Override
    public void deleteByMember(long memberId) {
        Avatar avatar = entityManager.find(Avatar.class, memberId);
        if (avatar != null) {
            entityManager.remove(avatar);
        }
    }
}
