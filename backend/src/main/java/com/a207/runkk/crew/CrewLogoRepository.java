package com.a207.runkk.crew;

import java.util.Optional;

/**
 * 크루 로고 저장소 포트(S15P31A207-295). 구현은 infra.crew.JpaCrewLogoRepository(PostgreSQL crew_logo.jpeg
 * BYTEA).
 */
public interface CrewLogoRepository {

    CrewLogo save(CrewLogo logo);

    Optional<CrewLogo> findById(long id);
}
