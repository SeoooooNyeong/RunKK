package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarFace;
import com.a207.runkk.avatar.AvatarRepository;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.Face;
import com.a207.runkk.avatar.FacePart;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Override
    public Map<Long, String> faceKeysOf(Collection<Long> memberIds) {
        List<Long> ids = memberIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select a.memberId, a.defaultCharacter, a.headJobId, key(p),"
                                        + " value(p) from Avatar a left join a.facePartKeys p where"
                                        + " a.memberId in :ids",
                                Object[].class)
                        .setParameter("ids", ids)
                        .getResultList();
        Map<Long, Object[]> faces = new LinkedHashMap<>();
        Map<Long, Map<FacePart, String>> parts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            faces.putIfAbsent(id, row);
            Map<FacePart, String> faceParts =
                    parts.computeIfAbsent(id, key -> new EnumMap<>(FacePart.class));
            if (row[3] != null) {
                faceParts.put((FacePart) row[3], (String) row[4]);
            }
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : ids) {
            Object[] row = faces.get(id);
            if (row != null) {
                new AvatarFace(id, (DefaultCharacter) row[1], (String) row[2], parts.get(id))
                        .face()
                        .map(Face::renderKey)
                        .ifPresent(key -> result.put(id, key));
            }
        }
        return result;
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
