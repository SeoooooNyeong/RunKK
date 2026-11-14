package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    /**
     * 얼굴 부위·착용(EAGER 값 컬렉션)을 fetch join으로 함께 읽어 회원마다 따로 읽지 않는다(N+1 방지). 두 컬렉션 모두 Map이라 bag 두 개 동시
     * fetch 제한에 걸리지 않고, 중복 행은 Hibernate가 엔티티 하나로 합친다.
     */
    @Override
    public List<Avatar> findAllByMemberIds(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "select a from Avatar a left join fetch a.facePartKeys"
                                + " left join fetch a.equipped where a.memberId in :ids",
                        Avatar.class)
                .setParameter("ids", memberIds)
                .getResultList();
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
