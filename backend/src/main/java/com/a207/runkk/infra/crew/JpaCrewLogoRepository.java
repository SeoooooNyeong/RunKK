package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.CrewLogo;
import com.a207.runkk.crew.CrewLogoRepository;
import jakarta.persistence.EntityManager;
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
}
