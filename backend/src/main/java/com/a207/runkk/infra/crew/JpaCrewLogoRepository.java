package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.CrewLogo;
import com.a207.runkk.crew.CrewLogoRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** crew.CrewLogoRepository의 JPA 구현(S15P31A207-295). 로고 바이트는 crew_logo.jpeg(BYTEA, V51)에 둔다. */
@Repository
public class JpaCrewLogoRepository implements CrewLogoRepository {

    private final EntityManager entityManager;

    public JpaCrewLogoRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CrewLogo save(CrewLogo logo) {
        entityManager.persist(logo);
        return logo;
    }

    @Override
    public Optional<CrewLogo> findById(long id) {
        return Optional.ofNullable(entityManager.find(CrewLogo.class, id));
    }

    @Override
    public Optional<Long> findUploaderId(long id) {
        return entityManager
                .createQuery("select l.uploaderId from CrewLogo l where l.id = :id", Long.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    /** 크루는 logo_url(URL_PREFIX + id)로만 로고를 가리킨다(FK 없음). */
    @Override
    public List<Long> findUnlinkedIds(long uploaderId) {
        return entityManager
                .createQuery(
                        "select l.id from CrewLogo l where l.uploaderId = :member and not exists"
                                + " (select c.id from Crew c where c.logoUrl ="
                                + " concat(:prefix, cast(l.id as String)))"
                                + " order by l.createdAt desc, l.id desc",
                        Long.class)
                .setParameter("member", uploaderId)
                .setParameter("prefix", CrewLogo.URL_PREFIX)
                .getResultList();
    }

    @Override
    public void removeAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        entityManager
                .createQuery("delete from CrewLogo l where l.id in :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
