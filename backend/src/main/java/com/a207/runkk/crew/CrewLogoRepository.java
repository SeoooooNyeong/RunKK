package com.a207.runkk.crew;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 크루 로고 저장소 포트(S15P31A207-295). 구현은 infra.crew.JpaCrewLogoRepository(PostgreSQL crew_logo.jpeg
 * BYTEA).
 */
public interface CrewLogoRepository {

    CrewLogo save(CrewLogo logo);

    Optional<CrewLogo> findById(long id);

    /** 로고를 올린 회원 ID(바이트는 읽지 않는다). 없으면 비어 있다. */
    Optional<Long> findUploaderId(long id);

    /** 그 회원이 올렸고 어느 크루의 logo_url도 가리키지 않는 로고 ID, 최신순(created_at, id 내림차순). */
    List<Long> findUnlinkedIds(long uploaderId);

    void removeAll(Collection<Long> ids);
}
