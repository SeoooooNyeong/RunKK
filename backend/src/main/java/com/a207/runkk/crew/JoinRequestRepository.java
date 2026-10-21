package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 가입 신청 저장소 포트(설계 6.1.7). 구현은 infra.crew.JpaJoinRequestRepository. */
public interface JoinRequestRepository {

    JoinRequest save(JoinRequest request);

    Optional<JoinRequest> findById(long id);

    boolean existsPending(long crewId, long memberId);

    List<JoinRequest> findPending(long crewId);

    List<JoinRequest> findPendingOlderThan(LocalDateTime cutoff);

    void deleteByCrew(long crewId);

    void deleteByMember(long memberId);
}
