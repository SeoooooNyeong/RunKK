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

    /** 그 회원의 대기 중 신청(신청 일시순). */
    List<JoinRequest> findPendingByMember(long memberId);

    List<JoinRequest> findPendingOlderThan(LocalDateTime cutoff);

    void deleteByCrew(long crewId);

    void deleteByMember(long memberId);
}
